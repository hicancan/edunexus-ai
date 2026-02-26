import pytest

from ai_service.kb import chunk_text, extract_text, sha1


def test_chunk_text_single_short_paragraph() -> None:
    text = "这是一段很短的文本。"
    chunks = chunk_text(text, chunk_size=700)
    assert len(chunks) == 1
    assert chunks[0] == text


def test_chunk_text_preserves_paragraph_boundaries() -> None:
    para1 = "第一段内容。" * 50  # ~300 chars
    para2 = "第二段内容。" * 50  # ~300 chars
    text = f"{para1}\n\n{para2}"
    chunks = chunk_text(text, chunk_size=700)
    # Both paragraphs fit in one chunk
    assert len(chunks) == 1


def test_chunk_text_splits_long_paragraphs_at_sentence() -> None:
    # Create a paragraph with many sentences exceeding chunk_size
    sentences = ["这是第{}句话。".format(i) for i in range(200)]
    text = "".join(sentences)
    chunks = chunk_text(text, chunk_size=100)
    assert len(chunks) > 1
    # Each chunk should be <= chunk_size (with possible slight overshoot for sentence)
    for c in chunks:
        assert len(c) <= 200  # generous upper bound


def test_chunk_text_empty_input() -> None:
    assert chunk_text("") == []
    assert chunk_text("   ") == []
    assert chunk_text("\n\n\n") == []


def test_sha1_deterministic() -> None:
    h1 = sha1("hello world")
    h2 = sha1("hello world")
    assert h1 == h2
    assert len(h1) == 40


def test_extract_text_missing_file() -> None:
    result = extract_text("/nonexistent/path/to/file.pdf")
    assert result == ""
