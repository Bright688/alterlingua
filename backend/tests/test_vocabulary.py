"""GET /v1/curriculum/{language}: the standard curriculum, independent of any user's own conversations."""


def test_a_page_of_french_words(client):
    response = client.get("/v1/curriculum/fr?offset=0&limit=5")
    assert response.status_code == 200
    body = response.json()
    assert body["language"] == "fr"
    assert body["offset"] == 0
    assert len(body["words"]) == 5
    assert body["total"] >= 2000  # a real, large list, not a handful of samples


def test_every_catalogue_language_has_a_curriculum(client):
    for language in ["en", "fr", "es", "de", "it", "nl", "zh", "ja"]:
        response = client.get(f"/v1/curriculum/{language}?limit=1")
        assert response.status_code == 200, language
        assert len(response.json()["words"]) == 1, language


def test_paging_moves_through_the_list_without_repeats(client):
    first = client.get("/v1/curriculum/en?offset=0&limit=10").json()["words"]
    second = client.get("/v1/curriculum/en?offset=10&limit=10").json()["words"]
    assert len(first) == 10
    assert len(second) == 10
    assert set(first).isdisjoint(second)


def test_past_the_end_of_the_list_returns_what_is_left_not_an_error(client):
    total = client.get("/v1/curriculum/en?limit=1").json()["total"]
    response = client.get(f"/v1/curriculum/en?offset={total - 2}&limit=10")
    assert response.status_code == 200
    assert len(response.json()["words"]) == 2

    response = client.get(f"/v1/curriculum/en?offset={total + 100}&limit=10")
    assert response.status_code == 200
    assert response.json()["words"] == []


def test_an_unknown_language_is_refused_cleanly(client):
    response = client.get("/v1/curriculum/xx?limit=1")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_language"


def test_a_malformed_language_code_is_refused_cleanly(client):
    response = client.get("/v1/curriculum/not-a-code-at-all?limit=1")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_language"


def test_a_regional_code_resolves_to_its_base_language(client):
    response = client.get("/v1/curriculum/fr-FR?limit=1")
    assert response.status_code == 200
    assert response.json()["language"] == "fr"


def test_offset_and_limit_are_bounded(client):
    assert client.get("/v1/curriculum/en?offset=-1").status_code == 422
    assert client.get("/v1/curriculum/en?limit=0").status_code == 422
    assert client.get("/v1/curriculum/en?limit=101").status_code == 422


def test_the_list_has_no_duplicate_words(client):
    total = client.get("/v1/curriculum/fr?limit=1").json()["total"]
    all_words: list[str] = []
    offset = 0
    while offset < total:
        page = client.get(f"/v1/curriculum/fr?offset={offset}&limit=100").json()["words"]
        all_words.extend(page)
        offset += 100
    assert len(all_words) == len(set(all_words))


def test_never_cached_like_every_other_v1_route(client):
    response = client.get("/v1/curriculum/en?limit=1")
    assert response.headers["cache-control"] == "no-store"
