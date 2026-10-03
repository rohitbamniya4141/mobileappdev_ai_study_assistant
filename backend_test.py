#!/usr/bin/env python3

import requests
import sys
import json
import time
from datetime import datetime
from pathlib import Path

class RAGChatbotTester:
    def __init__(self, base_url="https://azure-llm-assess.preview.emergentagent.com"):
        self.base_url = base_url
        self.api_url = f"{base_url}/api"
        self.tests_run = 0
        self.tests_passed = 0
        self.session_id = f"test_session_{int(time.time())}"
        self.uploaded_doc_id = None

    def log_test(self, name, success, details=""):
        """Log test results"""
        self.tests_run += 1
        if success:
            self.tests_passed += 1
            print(f"✅ {name} - PASSED")
        else:
            print(f"❌ {name} - FAILED: {details}")
        
        if details:
            print(f"   Details: {details}")

    def test_api_health(self):
        """Test basic API connectivity"""
        try:
            response = requests.get(f"{self.api_url}/", timeout=10)
            success = response.status_code == 200
            details = f"Status: {response.status_code}"
            if success:
                details += f", Response: {response.json()}"
            self.log_test("API Health Check", success, details)
            return success
        except Exception as e:
            self.log_test("API Health Check", False, str(e))
            return False

    def test_get_initial_stats(self):
        """Test getting initial system statistics"""
        try:
            response = requests.get(f"{self.api_url}/stats", timeout=10)
            success = response.status_code == 200
            if success:
                stats = response.json()
                details = f"Documents: {stats.get('documents', 0)}, Chunks: {stats.get('chunks', 0)}, FAQs: {stats.get('faqs', 0)}"
                self.log_test("Get Initial Stats", success, details)
                return stats
            else:
                self.log_test("Get Initial Stats", False, f"Status: {response.status_code}")
                return None
        except Exception as e:
            self.log_test("Get Initial Stats", False, str(e))
            return None

    def test_seed_faqs(self):
        """Test seeding FAQ data (bonus feature)"""
        try:
            response = requests.post(f"{self.api_url}/faqs/seed", timeout=15)
            success = response.status_code == 200
            if success:
                result = response.json()
                details = result.get('message', 'FAQs seeded')
                self.log_test("Seed FAQ Data", success, details)
                return True
            else:
                self.log_test("Seed FAQ Data", False, f"Status: {response.status_code}")
                return False
        except Exception as e:
            self.log_test("Seed FAQ Data", False, str(e))
            return False

    def test_get_faqs(self):
        """Test retrieving FAQ data"""
        try:
            response = requests.get(f"{self.api_url}/faqs", timeout=10)
            success = response.status_code == 200
            if success:
                faqs = response.json()
                details = f"Retrieved {len(faqs)} FAQs"
                self.log_test("Get FAQ Data", success, details)
                return faqs
            else:
                self.log_test("Get FAQ Data", False, f"Status: {response.status_code}")
                return []
        except Exception as e:
            self.log_test("Get FAQ Data", False, str(e))
            return []

    def test_upload_document(self):
        """Test PDF document upload and processing"""
        try:
            # Use sample document
            sample_file = "/app/sample_documents/machine_learning_basics.pdf"
            if not Path(sample_file).exists():
                self.log_test("Upload Document", False, f"Sample file not found: {sample_file}")
                return False

            with open(sample_file, 'rb') as f:
                files = {'file': ('machine_learning_basics.pdf', f, 'application/pdf')}
                response = requests.post(f"{self.api_url}/upload-document", files=files, timeout=60)
            
            success = response.status_code == 200
            if success:
                doc_data = response.json()
                self.uploaded_doc_id = doc_data.get('id')
                details = f"Document ID: {self.uploaded_doc_id}, Chunks: {doc_data.get('chunk_count', 0)}"
                self.log_test("Upload Document", success, details)
                return doc_data
            else:
                error_detail = ""
                try:
                    error_detail = response.json().get('detail', '')
                except:
                    error_detail = response.text
                self.log_test("Upload Document", False, f"Status: {response.status_code}, Error: {error_detail}")
                return None
        except Exception as e:
            self.log_test("Upload Document", False, str(e))
            return None

    def test_get_documents(self):
        """Test retrieving uploaded documents"""
        try:
            response = requests.get(f"{self.api_url}/documents", timeout=10)
            success = response.status_code == 200
            if success:
                docs = response.json()
                details = f"Retrieved {len(docs)} documents"
                self.log_test("Get Documents", success, details)
                return docs
            else:
                self.log_test("Get Documents", False, f"Status: {response.status_code}")
                return []
        except Exception as e:
            self.log_test("Get Documents", False, str(e))
            return []

    def test_rag_query(self, query, expected_sources=None):
        """Test RAG query functionality"""
        try:
            payload = {
                "query": query,
                "session_id": self.session_id
            }
            response = requests.post(f"{self.api_url}/query", json=payload, timeout=30)
            success = response.status_code == 200
            
            if success:
                result = response.json()
                response_text = result.get('response', '')
                sources = result.get('sources', [])
                
                details = f"Query: '{query}' | Response length: {len(response_text)} chars | Sources: {sources}"
                
                # Check if response is meaningful (not empty and has content)
                meaningful_response = len(response_text.strip()) > 10
                if not meaningful_response:
                    success = False
                    details += " | ERROR: Response too short or empty"
                
                self.log_test(f"RAG Query: {query[:30]}...", success, details)
                return result if success else None
            else:
                error_detail = ""
                try:
                    error_detail = response.json().get('detail', '')
                except:
                    error_detail = response.text
                self.log_test(f"RAG Query: {query[:30]}...", False, f"Status: {response.status_code}, Error: {error_detail}")
                return None
        except Exception as e:
            self.log_test(f"RAG Query: {query[:30]}...", False, str(e))
            return None

    def test_chat_history(self):
        """Test chat history retrieval"""
        try:
            response = requests.get(f"{self.api_url}/chat-history/{self.session_id}", timeout=10)
            success = response.status_code == 200
            if success:
                messages = response.json()
                details = f"Retrieved {len(messages)} messages for session {self.session_id}"
                self.log_test("Get Chat History", success, details)
                return messages
            else:
                self.log_test("Get Chat History", False, f"Status: {response.status_code}")
                return []
        except Exception as e:
            self.log_test("Get Chat History", False, str(e))
            return []

    def test_delete_document(self):
        """Test document deletion"""
        if not self.uploaded_doc_id:
            self.log_test("Delete Document", False, "No document ID available for deletion")
            return False
            
        try:
            response = requests.delete(f"{self.api_url}/documents/{self.uploaded_doc_id}", timeout=10)
            success = response.status_code == 200
            if success:
                result = response.json()
                details = result.get('message', 'Document deleted')
                self.log_test("Delete Document", success, details)
                return True
            else:
                self.log_test("Delete Document", False, f"Status: {response.status_code}")
                return False
        except Exception as e:
            self.log_test("Delete Document", False, str(e))
            return False

    def test_stats_after_changes(self):
        """Test stats after document operations"""
        try:
            response = requests.get(f"{self.api_url}/stats", timeout=10)
            success = response.status_code == 200
            if success:
                stats = response.json()
                details = f"Final - Documents: {stats.get('documents', 0)}, Chunks: {stats.get('chunks', 0)}, FAQs: {stats.get('faqs', 0)}"
                self.log_test("Get Final Stats", success, details)
                return stats
            else:
                self.log_test("Get Final Stats", False, f"Status: {response.status_code}")
                return None
        except Exception as e:
            self.log_test("Get Final Stats", False, str(e))
            return None

    def run_comprehensive_test(self):
        """Run all tests in sequence"""
        print("🚀 Starting RAG Chatbot Backend Testing")
        print(f"📍 Testing API at: {self.api_url}")
        print(f"🔑 Session ID: {self.session_id}")
        print("=" * 60)

        # Basic connectivity
        if not self.test_api_health():
            print("❌ API is not accessible. Stopping tests.")
            return False

        # Initial state
        initial_stats = self.test_get_initial_stats()
        
        # Bonus feature: FAQ seeding
        self.test_seed_faqs()
        faqs = self.test_get_faqs()
        
        # Document management
        doc_data = self.test_upload_document()
        documents = self.test_get_documents()
        
        # RAG functionality tests
        test_queries = [
            "What is machine learning?",
            "Explain linked lists and their time complexity", 
            "What are Python decorators?",
            "What is RAG?"  # This should test FAQ integration
        ]
        
        for query in test_queries:
            self.test_rag_query(query)
            time.sleep(1)  # Brief pause between queries
        
        # Chat history
        chat_messages = self.test_chat_history()
        
        # Cleanup
        self.test_delete_document()
        final_stats = self.test_stats_after_changes()
        
        # Summary
        print("=" * 60)
        print(f"📊 Test Results: {self.tests_passed}/{self.tests_run} passed")
        success_rate = (self.tests_passed / self.tests_run) * 100 if self.tests_run > 0 else 0
        print(f"📈 Success Rate: {success_rate:.1f}%")
        
        if success_rate >= 80:
            print("🎉 Backend testing completed successfully!")
            return True
        else:
            print("⚠️  Backend has significant issues that need attention.")
            return False

def main():
    tester = RAGChatbotTester()
    success = tester.run_comprehensive_test()
    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main())