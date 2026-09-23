// Thin JNI layer over librime's C API (librime: BSD-3-Clause). Everything runs on the phone; nothing is logged.
#include <jni.h>
#include <rime_api.h>

#include <android/log.h>

#include <string>
#include <vector>

namespace {

constexpr size_t kMaxCandidates = 30;

std::string ToString(JNIEnv* env, jstring value) {
  const char* chars = env->GetStringUTFChars(value, nullptr);
  std::string result(chars ? chars : "");
  if (chars) env->ReleaseStringUTFChars(value, chars);
  return result;
}

RimeApi* Api() { return rime_get_api(); }

jobjectArray ToArray(JNIEnv* env, const std::vector<std::string>& items) {
  jclass string_class = env->FindClass("java/lang/String");
  jobjectArray array = env->NewObjectArray(static_cast<jsize>(items.size()), string_class, nullptr);
  for (size_t i = 0; i < items.size(); ++i) {
    jstring value = env->NewStringUTF(items[i].c_str());
    env->SetObjectArrayElement(array, static_cast<jsize>(i), value);
    env->DeleteLocalRef(value);
  }
  return array;
}

}  // namespace

extern "C" {

// Sets up librime and compiles the schema (slow the first time, quick afterwards). Blocking.
JNIEXPORT jboolean JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_start(
    JNIEnv* env, jclass, jstring shared_dir, jstring user_dir, jstring schema_id) {
  static std::string shared, user;
  shared = ToString(env, shared_dir);
  user = ToString(env, user_dir);
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = shared.c_str();
  traits.user_data_dir = user.c_str();
  traits.distribution_name = "AlterLingua";
  traits.distribution_code_name = "alterlingua";
  traits.distribution_version = "1";
  traits.app_name = "rime.alterlingua";
  traits.min_log_level = 3;  // fatal only; no log files
  static bool started = false;  // set up once per process; more engines only add sessions
  if (started) return True;
  RimeApi* api = Api();
  api->setup(&traits);
  api->initialize(&traits);
  if (api->start_maintenance(True)) api->join_maintenance_thread();
  started = true;
  return True;
}

JNIEXPORT jlong JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_createSession(
    JNIEnv* env, jclass, jstring schema_id) {
  RimeApi* api = Api();
  RimeSessionId session = api->create_session();
  if (session != 0) {
    const std::string schema = ToString(env, schema_id);
    const bool ok = api->select_schema(session, schema.c_str());
    // Debug aid: which schema could not be selected (never any typed text).
    if (!ok) __android_log_print(ANDROID_LOG_WARN, "alterlingua-rime", "could not select schema %s", schema.c_str());
  }
  return static_cast<jlong>(session);
}

JNIEXPORT void JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_destroySession(JNIEnv*, jclass, jlong session) {
  Api()->destroy_session(static_cast<RimeSessionId>(session));
}

JNIEXPORT jboolean JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_processKey(
    JNIEnv*, jclass, jlong session, jint keycode, jint mask) {
  return Api()->process_key(static_cast<RimeSessionId>(session), keycode, mask) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_clear(JNIEnv*, jclass, jlong session) {
  Api()->clear_composition(static_cast<RimeSessionId>(session));
}

JNIEXPORT jboolean JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_selectCandidate(
    JNIEnv*, jclass, jlong session, jint index) {
  return Api()->select_candidate(static_cast<RimeSessionId>(session), static_cast<size_t>(index)) ? JNI_TRUE : JNI_FALSE;
}

// Returns [committedText, typedInput, candidate0, candidate1, ...]. The committed text is taken (returned once).
JNIEXPORT jobjectArray JNICALL Java_com_alterlingua_app_keyboard_engine_RimeJni_snapshot(JNIEnv* env, jclass, jlong session) {
  RimeApi* api = Api();
  const RimeSessionId id = static_cast<RimeSessionId>(session);
  std::vector<std::string> out;

  RIME_STRUCT(RimeCommit, commit);
  if (api->get_commit(id, &commit)) {
    out.push_back(commit.text ? commit.text : "");
    api->free_commit(&commit);
  } else {
    out.push_back("");
  }

  // The raw input as typed, then what to show for it: the engine's own reading (pinyin with the syllables split) when it has one.
  const char* input = api->get_input(id);
  const std::string raw = input ? input : "";
  std::string shown;
  RIME_STRUCT(RimeContext, context);
  if (api->get_context(id, &context)) {
    if (context.composition.preedit) shown = context.composition.preedit;
    api->free_context(&context);
  }
  if (shown.empty()) shown = raw;
  out.push_back(raw);
  out.push_back(shown);

  RimeCandidateListIterator it = {0};
  if (api->candidate_list_begin(id, &it)) {
    size_t count = 0;
    while (count < kMaxCandidates && api->candidate_list_next(&it)) {
      out.push_back(it.candidate.text ? it.candidate.text : "");
      ++count;
    }
    api->candidate_list_end(&it);
  }
  return ToArray(env, out);
}

}  // extern "C"
