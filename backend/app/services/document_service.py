import io
import re

from docx import Document as DocxDocument
from docx.opc.exceptions import PackageNotFoundError
from pypdf import PdfReader
from pypdf.errors import PdfReadError


class InvalidDocumentError(ValueError):
    """The uploaded document cannot safely be processed."""


# ---------------------------------------------------------------------------
# PDF support (unchanged)
# ---------------------------------------------------------------------------

def validate_pdf(content: bytes) -> None:
    if not content or not content.lstrip().startswith(b"%PDF"):
        raise InvalidDocumentError("The uploaded file is not a valid PDF.")


def extract_text_from_pdf(content: bytes) -> str:
    validate_pdf(content)
    try:
        reader = PdfReader(io.BytesIO(content))
        if reader.is_encrypted:
            raise InvalidDocumentError("Password-protected PDFs are not supported.")
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
    except PdfReadError as exc:
        raise InvalidDocumentError("The PDF is malformed or cannot be read.") from exc
    except InvalidDocumentError:
        raise
    except Exception as exc:
        raise InvalidDocumentError("Unable to extract text from this PDF.") from exc
    cleaned = re.sub(r"[ \t]+", " ", text)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned).strip()
    if not cleaned:
        raise InvalidDocumentError("No extractable text was found in the PDF.")
    return cleaned


# ---------------------------------------------------------------------------
# DOCX support (new)
# ---------------------------------------------------------------------------

# All DOCX files are ZIP archives; their first 4 bytes are the PK magic number.
_DOCX_MAGIC = b"PK\x03\x04"


def validate_docx(content: bytes) -> None:
    """Verify the file is a real ZIP/Office Open XML document via magic bytes."""
    if not content or not content.startswith(_DOCX_MAGIC):
        raise InvalidDocumentError(
            "The uploaded file is not a valid DOCX document. "
            "Only real Office Open XML (.docx) files are accepted."
        )


def extract_text_from_docx(content: bytes) -> str:
    """Extract plain text from a DOCX file including paragraphs and table cells."""
    validate_docx(content)
    try:
        doc = DocxDocument(io.BytesIO(content))
        parts: list[str] = []

        # Normal paragraphs
        for para in doc.paragraphs:
            stripped = para.text.strip()
            if stripped:
                parts.append(stripped)

        # Table cells — iterate every table, row, and cell
        for table in doc.tables:
            for row in table.rows:
                row_texts = [cell.text.strip() for cell in row.cells if cell.text.strip()]
                if row_texts:
                    parts.append(" | ".join(row_texts))

    except PackageNotFoundError as exc:
        raise InvalidDocumentError(
            "The DOCX file is corrupted or not a valid Office document."
        ) from exc
    except InvalidDocumentError:
        raise
    except Exception as exc:
        raise InvalidDocumentError("Unable to extract text from this DOCX file.") from exc

    text = "\n".join(parts)
    cleaned = re.sub(r"[ \t]+", " ", text)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned).strip()
    if not cleaned:
        raise InvalidDocumentError("No extractable text was found in the DOCX file.")
    return cleaned

