package com.example.expenseapp.core.util

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OCR Engine Interface for FOSS compliance.
 * We'll use a placeholder implementation that simulates scanning.
 * In a real F-Droid app, you'd use Tesseract or a local ML model.
 */
interface OCREngine {
    data class ScanResult(
        val amount: Double?,
        val date: Long?,
        val description: String?
    )

    suspend fun scanReceipt(bitmap: Bitmap): ScanResult
}

class FakeOCREngine : OCREngine {
    override suspend fun scanReceipt(bitmap: Bitmap): OCREngine.ScanResult = withContext(Dispatchers.Default) {
        // Simulating processing delay
        kotlinx.coroutines.delay(1500)
        
        // Mocking a successful scan
        OCREngine.ScanResult(
            amount = 42.50,
            date = System.currentTimeMillis(),
            description = "Dinner at Antigravity Bistro"
        )
    }
}
