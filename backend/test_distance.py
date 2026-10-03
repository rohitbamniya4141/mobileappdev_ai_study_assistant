import asyncio
from pathlib import Path
from app.services.rag_service import RAGService

async def main():
    rag = RAGService(chroma_path=Path("chroma_data"), model_name="all-MiniLM-L6-v2", chunk_size=1000, chunk_overlap=200)
    embedding = (await asyncio.to_thread(rag._embed, ["what is cricket"]))[0]
    results = await asyncio.to_thread(rag._get_collection().query, query_embeddings=[embedding], n_results=5)
    
    print("Distances for 'what is cricket':")
    distances = (results.get("distances") or [[]])[0]
    documents = (results.get("documents") or [[]])[0]
    
    for dist, doc in zip(distances, documents):
        print(f"Distance: {dist:.4f} | Chunk preview: {doc[:50]}...")

if __name__ == '__main__':
    asyncio.run(main())
