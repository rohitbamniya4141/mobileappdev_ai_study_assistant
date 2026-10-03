import pytest

from app.services.document_service import InvalidDocumentError, extract_text_from_pdf


def test_rejects_non_pdf_content() -> None:
    with pytest.raises(InvalidDocumentError, match="valid PDF"):
        extract_text_from_pdf(b"not a pdf")


def test_rejects_pdf_without_extractable_text() -> None:
    with pytest.raises(InvalidDocumentError):
        extract_text_from_pdf(b"%PDF-1.4\n%%EOF")
