package com.google.android.apps.inputmethod.libs.mozc.session;

/**
 * Entry points into Mozc's native library (libmozc.so). The package and class name are fixed by the library, which registers
 * these methods by that name, so this class must stay here and keep these signatures.
 */
public final class MozcJni {
  private MozcJni() {}

  /** Registers the native methods below. Must be called once, after loading the library. */
  public static native boolean initialize();

  /** Sends one serialized mozc.commands.Command and returns the serialized answer. */
  public static native byte[] evalCommand(byte[] command);

  /** Creates the conversion engine from the data file. Returns true if it succeeded or was already done. */
  public static native boolean onPostLoad(String userProfileDirectory, String dataFilePath);

  public static native String getDataVersion();
}
