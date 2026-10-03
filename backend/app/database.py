from collections.abc import Awaitable
from typing import TypeVar

from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase

from app.config import Settings

T = TypeVar("T")


class DatabaseUnavailableError(RuntimeError):
    """Raised when a MongoDB operation cannot be completed."""


class MongoDatabase:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client: AsyncIOMotorClient | None = None

    @property
    def database(self) -> AsyncIOMotorDatabase:
        if not self._settings.mongodb_url:
            raise DatabaseUnavailableError("MongoDB is not configured.")
        if self._client is None:
            self._client = AsyncIOMotorClient(self._settings.mongodb_url, serverSelectionTimeoutMS=3000)
        return self._client[self._settings.mongodb_database]

    async def ping(self) -> bool:
        try:
            await self.database.command("ping")
            return True
        except Exception:
            return False

    def close(self) -> None:
        if self._client is not None:
            self._client.close()


async def run_db(operation: Awaitable[T]) -> T:
    try:
        return await operation
    except DatabaseUnavailableError:
        raise
    except Exception as exc:
        raise DatabaseUnavailableError("Database operation failed.") from exc
