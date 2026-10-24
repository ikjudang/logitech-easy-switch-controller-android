package com.easyswitchcontroller.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 핸드오프 assets/icons.html의 SVG 글리프를 ImageVector로 옮긴 것.
 * 전부 흰색으로 그려서 사용처에서 [androidx.compose.material3.Icon]의 tint로 칠한다.
 */
object AppIcons {

    /** 마우스: 세로 캡슐 외곽선 + 스크롤 휠 라인. */
    val Mouse: ImageVector by lazy {
        icon("Mouse") {
            stroke(2f) {
                moveTo(12f, 2f)
                arcTo(5f, 5f, 0f, false, true, 17f, 7f)
                lineTo(17f, 17f)
                arcTo(5f, 5f, 0f, false, true, 12f, 22f)
                arcTo(5f, 5f, 0f, false, true, 7f, 17f)
                lineTo(7f, 7f)
                arcTo(5f, 5f, 0f, false, true, 12f, 2f)
                close()
            }
            stroke(2f) { moveTo(12f, 5f); lineTo(12f, 9f) }
        }
    }

    /** 키보드: 외곽선 + 키 도트 4개 + 스페이스 바. viewBox 28x24(원본 비율 유지). */
    val Keyboard: ImageVector by lazy {
        icon("Keyboard", viewportWidth = 28f, viewportHeight = 24f) {
            stroke(2f) {
                moveTo(6f, 5f)
                lineTo(22f, 5f)
                arcTo(3f, 3f, 0f, false, true, 25f, 8f)
                lineTo(25f, 17f)
                arcTo(3f, 3f, 0f, false, true, 22f, 20f)
                lineTo(6f, 20f)
                arcTo(3f, 3f, 0f, false, true, 3f, 17f)
                lineTo(3f, 8f)
                arcTo(3f, 3f, 0f, false, true, 6f, 5f)
                close()
            }
            // 라운드 캡의 극소 선분 = 키 도트(원본 SVG의 zero-length line 기법)
            listOf(7f, 12f, 17f, 21f).forEach { x ->
                stroke(2f) { moveTo(x, 9f); lineTo(x + 0.02f, 9f) }
            }
            stroke(2f) { moveTo(9f, 15f); lineTo(19f, 15f) }
        }
    }

    /** 홈(면 채움). */
    val Home: ImageVector by lazy {
        icon("Home") {
            fill {
                moveTo(12f, 3f)
                lineTo(21f, 11f)
                lineTo(18.5f, 11f)
                lineTo(18.5f, 20f)
                lineTo(5.5f, 20f)
                lineTo(5.5f, 11f)
                lineTo(3f, 11f)
                close()
            }
        }
    }

    /** 기기 목록(가로 3줄). */
    val Devices: ImageVector by lazy {
        icon("Devices") {
            listOf(7f, 12f, 17f).forEach { y ->
                stroke(2.4f) { moveTo(4f, y); lineTo(20f, y) }
            }
        }
    }

    /** 설정 톱니바퀴 — 표준 Material 기어(이빨 있는 외곽 + 중앙 구멍). 사용자 확정: 햇살형 금지. */
    val Gear: ImageVector by lazy {
        ImageVector.Builder(
            name = "Gear",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
                        "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
                        "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
                        "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
                        "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
                        "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
                        "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                        "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                        "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
                        "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94z" +
                        "M12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
                ),
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd,
            )
        }.build()
    }

    /** 언어 지구본(원 + 가로선 + 세로 타원). */
    val Globe: ImageVector by lazy {
        icon("Globe") {
            stroke(2f) {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            }
            stroke(2f) { moveTo(3f, 12f); lineTo(21f, 12f) }
            stroke(2f) {
                moveTo(12f, 3f)
                arcTo(4f, 9f, 0f, false, true, 12f, 21f)
                arcTo(4f, 9f, 0f, false, true, 12f, 3f)
            }
        }
    }

    /** 시계(연결 로그 행 리딩 아이콘). */
    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke(2f) {
                moveTo(4f, 12f)
                arcTo(8f, 8f, 0f, true, true, 20f, 12f)
                arcTo(8f, 8f, 0f, true, true, 4f, 12f)
            }
            stroke(2f) { moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.2f, 13.8f) }
        }
    }

    /** 폴더(로그 저장 폴더 카드). */
    val Folder: ImageVector by lazy {
        icon("Folder") {
            stroke(2f) {
                moveTo(3f, 7f)
                lineTo(9.5f, 7f)
                lineTo(11.5f, 9.5f)
                lineTo(21f, 9.5f)
                lineTo(21f, 18f)
                lineTo(3f, 18f)
                close()
            }
        }
    }

    /** 오른쪽 셰브론. */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroke(3f) { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }
        }
    }

    /** 아래 셰브론(드롭다운 트리거). */
    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            stroke(3f) { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
        }
    }

    /** 위 셰브론(펼쳐진 드롭다운). */
    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            stroke(3f) { moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f) }
        }
    }

    /** 뒤로(왼쪽 셰브론). */
    val Back: ImageVector by lazy {
        icon("Back") {
            stroke(3f) { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
        }
    }

    /** 체크(허용됨·선택 체크박스). */
    val Check: ImageVector by lazy {
        icon("Check") {
            stroke(3.5f) { moveTo(4f, 12f); lineTo(10f, 18f); lineTo(20f, 6f) }
        }
    }

    /** 새로고침: 동그라미 화살표 — 270° 호 + 화살촉 1개, 180° + 반시계 45° 회전형(사용자 확정). */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            group(rotate = -45f, pivotX = 12f, pivotY = 12f) {
                stroke(2.2f) {
                    moveTo(12f, 19f)
                    arcTo(7f, 7f, 0f, true, true, 19f, 12f)
                }
                // 호 끝에 진행 방향 화살촉
                fill {
                    moveTo(19f, 17.4f)
                    lineTo(15.6f, 11f)
                    lineTo(22.4f, 11f)
                    close()
                }
            }
        }
    }

    /** 브랜드 마크: 양방향 스왑 화살표(레일 로고 타일·런처와 동일 모티프). */
    val Swap: ImageVector by lazy {
        icon("Swap") {
            fill {
                moveTo(4f, 8f); lineTo(13f, 8f); lineTo(13f, 6f); lineTo(19f, 9.5f)
                lineTo(13f, 13f); lineTo(13f, 11f); lineTo(4f, 11f); close()
            }
            fill {
                moveTo(20f, 13f); lineTo(11f, 13f); lineTo(11f, 11f); lineTo(5f, 14.5f)
                lineTo(11f, 18f); lineTo(11f, 16f); lineTo(20f, 16f); close()
            }
        }
    }

    /**
     * 브랜드 아트(런처 포그라운드와 동일 지오메트리): 태블릿 ↔ 스왑 화살표 ↔ 모니터.
     * macOS 헤더와 같은 1.16배 전경·16 단위 선 두께. 원본 중심(270, 256)을
     * 뷰포트 중심(256, 256)에 맞춰 바탕 폭의 약 75.2%를 차지한다.
     */
    val Brand: ImageVector by lazy {
        icon("Brand", viewportWidth = 512f, viewportHeight = 512f) {
            group(scaleX = 1.16f, scaleY = 1.16f, translationX = -57.2f, translationY = -40.96f) {
                // 태블릿(좌) + 홈 버튼
                stroke(16f) {
                    moveTo(125f, 188f)
                    lineTo(169f, 188f)
                    arcTo(13f, 13f, 0f, false, true, 182f, 201f)
                    lineTo(182f, 311f)
                    arcTo(13f, 13f, 0f, false, true, 169f, 324f)
                    lineTo(125f, 324f)
                    arcTo(13f, 13f, 0f, false, true, 112f, 311f)
                    lineTo(112f, 201f)
                    arcTo(13f, 13f, 0f, false, true, 125f, 188f)
                    close()
                }
                stroke(16f) { moveTo(139f, 311f); lineTo(155f, 311f) }
                // 모니터(우) + 받침
                stroke(16f) {
                    moveTo(341f, 200f)
                    lineTo(417f, 200f)
                    arcTo(11f, 11f, 0f, false, true, 428f, 211f)
                    lineTo(428f, 263f)
                    arcTo(11f, 11f, 0f, false, true, 417f, 274f)
                    lineTo(341f, 274f)
                    arcTo(11f, 11f, 0f, false, true, 330f, 263f)
                    lineTo(330f, 211f)
                    arcTo(11f, 11f, 0f, false, true, 341f, 200f)
                    close()
                }
                stroke(16f) { moveTo(379f, 274f); lineTo(379f, 296f) }
                stroke(16f) { moveTo(356f, 299f); lineTo(402f, 299f) }
                // 양방향 화살표(면)
                fill {
                    moveTo(219f, 228f); lineTo(269f, 228f)
                    arcTo(7f, 7f, 0f, false, true, 276f, 235f); lineTo(276f, 236f)
                    arcTo(7f, 7f, 0f, false, true, 269f, 243f); lineTo(219f, 243f)
                    arcTo(7f, 7f, 0f, false, true, 212f, 236f); lineTo(212f, 235f)
                    arcTo(7f, 7f, 0f, false, true, 219f, 228f); close()
                }
                fill { moveTo(274f, 216f); lineTo(300f, 235.5f); lineTo(274f, 255f); close() }
                fill {
                    moveTo(243f, 270f); lineTo(293f, 270f)
                    arcTo(7f, 7f, 0f, false, true, 300f, 277f); lineTo(300f, 278f)
                    arcTo(7f, 7f, 0f, false, true, 293f, 285f); lineTo(243f, 285f)
                    arcTo(7f, 7f, 0f, false, true, 236f, 278f); lineTo(236f, 277f)
                    arcTo(7f, 7f, 0f, false, true, 243f, 270f); close()
                }
                fill { moveTo(238f, 262f); lineTo(212f, 281.5f); lineTo(238f, 301f); close() }
            }
        }
    }
}

private fun icon(
    name: String,
    viewportWidth: Float = 24f,
    viewportHeight: Float = 24f,
    block: ImageVector.Builder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = viewportWidth.dp,
        defaultHeight = viewportHeight.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    ).apply(block).build()

private fun ImageVector.Builder.stroke(
    width: Float,
    cap: StrokeCap = StrokeCap.Round,
    pathBuilder: PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
        strokeLineCap = cap,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}

private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) {
    path(fill = SolidColor(Color.White), pathBuilder = pathBuilder)
}
