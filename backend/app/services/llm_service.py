import logging

from langchain_core.prompts import ChatPromptTemplate
from openai import APIConnectionError, APIError, AsyncOpenAI, AuthenticationError

logger = logging.getLogger(__name__)


class LLMUnavailableError(RuntimeError):
    """A configured LLM cannot generate a response."""


class LLMService:
    """LLM integration layer with config-based provider switching.

    Supported providers
    -------------------
    * ``openai``  — OpenAI API (requires OPENAI_API_KEY).
    * ``ollama``  — Local Ollama server via its OpenAI-compatible endpoint.
    * ``gemini``  — Google Gemini via its OpenAI-compatible REST endpoint
                    (requires GEMINI_API_KEY).  No extra SDK needed — the same
                    ``AsyncOpenAI`` client is reused with a custom base URL, so
                    the entire answer/prompt pipeline is untouched.

    Switching providers requires only environment variable changes; no code change.

    Uses LangChain's ChatPromptTemplate to define the grounded system prompt,
    keeping prompt logic declarative and provider-agnostic.
    """

    # LangChain ChatPromptTemplate for grounded, hallucination-resistant Q&A
    # Using tuple syntax ("system", ...) / ("human", ...) so {context} and
    # {question} are treated as template variables and properly substituted.
    _prompt_template = ChatPromptTemplate.from_messages([
        (
            "system",
            "You are a strict study assistant. You must answer the user's question ONLY using "
            "the provided context. If the provided context does not contain the answer, you must "
            "reply with: 'I cannot answer this because the information is not in the uploaded "
            "documents.' Do not use your outside knowledge.",
        ),
        ("human", "Context:\n{context}\n\nQuestion: {question}"),
    ])

    # Gemini's OpenAI-compatible base URL (no trailing-slash issues — AsyncOpenAI
    # appends the path segments correctly when the URL ends with a slash).
    _GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"

    def __init__(
        self,
        provider: str,
        openai_api_key: str | None,
        model: str,
        ollama_base_url: str,
        gemini_api_key: str | None = None,
    ) -> None:
        self._provider = provider
        self._openai_api_key = openai_api_key
        self._model = model
        self._ollama_base_url = ollama_base_url
        self._gemini_api_key = gemini_api_key

    @property
    def configured(self) -> bool:
        if self._provider == "ollama":
            return True
        if self._provider == "gemini":
            return bool(self._gemini_api_key)
        # openai
        return bool(self._openai_api_key)

    def _client(self) -> AsyncOpenAI:
        """Return an AsyncOpenAI client configured for the active provider.

        All three providers are accessed through the OpenAI client:
        - Ollama   → local OpenAI-compatible endpoint, dummy key
        - OpenAI   → standard openai.com endpoint, real key
        - Gemini   → Google's OpenAI-compatible endpoint, Gemini API key

        This keeps the ``answer()`` method completely provider-agnostic.
        """
        if self._provider == "ollama":
            # Ollama exposes an OpenAI-compatible local endpoint; placeholder key is ignored.
            return AsyncOpenAI(api_key="ollama", base_url=self._ollama_base_url)

        if self._provider == "gemini":
            if not self._gemini_api_key:
                raise LLMUnavailableError(
                    "The Gemini API key has not been configured (GEMINI_API_KEY)."
                )
            # Google exposes an OpenAI-compatible endpoint for Gemini models.
            # Docs: https://ai.google.dev/gemini-api/docs/openai
            return AsyncOpenAI(
                api_key=self._gemini_api_key,
                base_url=self._GEMINI_BASE_URL,
            )

        # Default: openai
        if not self._openai_api_key:
            raise LLMUnavailableError("The OpenAI API key has not been configured.")
        return AsyncOpenAI(api_key=self._openai_api_key)

    async def answer(self, context: str, question: str) -> str:
        """Generate a grounded answer using the configured LLM provider.

        The prompt template, message format, and completion parameters are
        identical across all providers — only the underlying HTTP client target
        differs (determined by ``_client()``).
        """
        # Format messages using the LangChain prompt template
        messages = self._prompt_template.format_messages(
            context=context, question=question
        )
        openai_messages = [
            {"role": ("system" if msg.type == "system" else "user"), "content": msg.content}
            for msg in messages
        ]
        try:
            client = self._client()
            response = await client.chat.completions.create(
                model=self._model,
                temperature=0.2,
                messages=openai_messages,
            )
            content = response.choices[0].message.content
            if not content:
                raise LLMUnavailableError("The LLM returned an empty response.")
            return content
        except LLMUnavailableError:
            raise
        except AuthenticationError as exc:
            # Do NOT log the key itself — just the provider name
            logger.error("Authentication failed for provider '%s'.", self._provider)
            raise LLMUnavailableError(
                f"Authentication failed for the {self._provider} provider. "
                "Check your API key configuration."
            ) from exc
        except APIConnectionError as exc:
            logger.error("Connection error for provider '%s': %s", self._provider, exc)
            raise LLMUnavailableError(
                f"Cannot reach the {self._provider} service. "
                "Check your network or provider URL."
            ) from exc
        except APIError as exc:
            logger.error("API error from provider '%s': %s", self._provider, exc)
            raise LLMUnavailableError("The LLM service is currently unavailable.") from exc
