package com.easyswitchcontroller.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 회귀 규칙(사용자 확정): **모든 선택(리플) 효과는 그 버튼의 형태를 따른다.**
 *
 * Compose에서 리플은 클릭 Modifier 시점의 클립 경계를 쓰므로, 둥근 배경 위에
 * `.clip(...)` 없이 `.clickable`을 걸면 리플이 사각형으로 모서리를 삐져나온다.
 * 이 테스트는 ui/ 소스의 Modifier 체인을 스캔해 "shape 있는 background + clickable인데
 * clip이 없는" 체인을 컴파일 타임에 잡는 소스 린트다(기기 없이 JVM에서 실행).
 */
class RippleShapeRegressionTest {

    @Test
    fun `둥근 배경을 가진 클릭 체인은 반드시 clip을 함께 건다`() {
        val uiDir = sequenceOf(
            File("src/main/java/com/easyswitchcontroller/ui"),
            File("app/src/main/java/com/easyswitchcontroller/ui"),
        ).firstOrNull { it.isDirectory }
        assertTrue("ui 소스 디렉터리를 찾지 못함 (작업 디렉터리 확인)", uiDir != null)

        val violations = mutableListOf<String>()
        uiDir!!.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            modifierChains(file.readText()).forEach { chain ->
                val clickable = ".clickable" in chain || ".combinedClickable" in chain
                val roundedBg = Regex("""background\([^)]*[Ss]hape""").containsMatchIn(chain) ||
                    ("background(" in chain && "RoundedCornerShape" in chain)
                if (clickable && roundedBg && ".clip(" !in chain) {
                    violations.add("${file.name}: ${chain.take(120).replace('\n', ' ')}…")
                }
            }
        }
        assertTrue(
            "clip 없는 둥근 클릭 체인 발견(리플이 모서리를 삐져나옴):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /** 소스에서 `Modifier.…` 체인(중첩 괄호·람다 포함)을 통째로 추출한다. */
    private fun modifierChains(src: String): List<String> {
        val chains = mutableListOf<String>()
        var i = src.indexOf("Modifier")
        while (i >= 0) {
            var j = i + "Modifier".length
            while (true) {
                var k = j
                while (k < src.length && src[k].isWhitespace()) k++
                if (k >= src.length || src[k] != '.') break
                k++ // '.'
                while (k < src.length && (src[k].isLetterOrDigit() || src[k] == '_')) k++
                // 뒤따르는 (…)·{…} 블록(체인 인자·람다)을 짝 맞춰 통째로 소비
                while (k < src.length) {
                    var m = k
                    while (m < src.length && src[m].isWhitespace()) m++
                    if (m < src.length && (src[m] == '(' || src[m] == '{')) {
                        k = skipBalanced(src, m)
                    } else {
                        break
                    }
                }
                j = k
            }
            if (j > i + "Modifier".length) chains.add(src.substring(i, j))
            i = src.indexOf("Modifier", maxOf(j, i + 1))
        }
        return chains
    }

    /** [start]의 여는 괄호부터 짝이 맞는 닫는 괄호 다음 인덱스까지 소비. */
    private fun skipBalanced(src: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < src.length) {
            when (src[i]) {
                '(', '{' -> depth++
                ')', '}' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return i
    }
}
