    fun saveData(showToast: Boolean = false) {
        try {
            val prefs = context.getSharedPreferences("foldstit_data", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("text", memoText)
                putString("theme", selectedTheme.name)
                putInt("fontSize", fontSize)
                putBoolean("hasDrawing", paths.isNotEmpty())
                apply()
            }

            // 위젯 프로세스 강제 갱신 트리거
            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
                FoldstItWidget.notifyUpdate(context)
            }

            if (showToast) {
                Toast.makeText(context, "메모가 저장되고 위젯에 반영되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
