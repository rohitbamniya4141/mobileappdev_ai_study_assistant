import asyncio
import time
import os
from pathlib import Path
from app.services.rag_service import RAGService
from app.services.llm_service import LLMService

async def main():
    print("Starting Benchmark...")
    
    # 1. Initialize RAG Service (ChromaDB + SentenceTransformers)
    chroma_path = Path("chroma_data_benchmark")
    rag = RAGService(chroma_path=chroma_path, model_name="all-MiniLM-L6-v2", chunk_size=1000, chunk_overlap=200)
    
    # Generate 100 pages of dummy text (roughly 30,000 words)
    print("\n--- Testing Document Ingestion ---")
    dummy_text = "This is a sentence about biology and photosynthesis. " * 3000
    
    start_chunk = time.perf_counter()
    chunks = rag.chunk_text(dummy_text)
    end_chunk = time.perf_counter()
    print(f"Chunked 100-page text into {len(chunks)} chunks in: {(end_chunk - start_chunk):.4f} seconds")
    
    start_index = time.perf_counter()
    await rag.add_document("doc_bench_1", "benchmark.pdf", chunks)
    end_index = time.perf_counter()
    print(f"Embedded and Indexed {len(chunks)} chunks into ChromaDB in: {(end_index - start_index):.4f} seconds")
    
    # 2. Test ChromaDB Retrieval Latency
    print("\n--- Testing ChromaDB Semantic Search Latency ---")
    retrieval_times = []
    for _ in range(5):
        start_ret = time.perf_counter()
        res = await rag.retrieve("What is photosynthesis?", 4)
        end_ret = time.perf_counter()
        retrieval_times.append(end_ret - start_ret)
    
    avg_latency = sum(retrieval_times) / len(retrieval_times)
    print(f"Average ChromaDB Retrieval Latency: {avg_latency * 1000:.2f} ms")
    print(f"Sources retrieved: {res.sources}")
    
    # 3. Test Ollama
    print("\n--- Testing Local LLM (Ollama) ---")
    try:
        import httpx
        # Check if Ollama is actually running locally
        resp = httpx.get("http://127.0.0.1:11434/")
        if resp.status_code == 200:
            llm = LLMService(provider="ollama", api_key="", model="llama3.2", ollama_base_url="http://127.0.0.1:11434/v1")
            start_llm = time.perf_counter()
            answer = await llm.answer("Biology is the study of life.", "What is biology?")
            end_llm = time.perf_counter()
            print(f"Ollama responded in: {(end_llm - start_llm):.2f} seconds")
            print(f"Answer: {answer}")
        else:
            print("Ollama is not running on port 11434.")
    except Exception as e:
        print(f"Ollama is not running or unreachable: {e}")

if __name__ == '__main__':
    asyncio.run(main())
