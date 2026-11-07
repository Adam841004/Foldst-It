    // 영속성 저장 및 위젯 강제 업데이트 브로드캐스트
    fun saveData(showToast: Boolean = false) {
        try {
            val file = File(context.filesDir, "foldstit_data.json")
            val json = JSONObject().apply {
                put("text", memoText)
                put("theme", selectedTheme.name)
                put("fontSize", fontSize)

                val pathsArray = JSONArray()
                paths.forEach { drawPath ->
                    val pathObj = JSONObject().apply {
                        put("color", drawPath.hexColor)
                        put("width", drawPath.strokeWidth)
                        val pointsArray = JSONArray()
                        drawPath.points.forEach { pt ->
                            pointsArray.put(JSONObject().apply {
                                put("x", pt.x)
                                put("y", pt.y)
                            })
                        }
                        put("points", pointsArray)
                    }
                    pathsArray.put(pathObj)
                }
                put("paths", pathsArray)
            }
            file.writeText(json.toString())

            // Glance 내부 비동기 갱신 + 시스템 브로드캐스트 동시 발송
            CoroutineScope(Dispatchers.IO).launch {
                FoldstItWidget().updateAll(context)
                FoldstItWidget.notifyUpdate(context)
            }

            if (showToast) {
                Toast.makeText(context, "메모가 저장되고 위젯에 반영되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
