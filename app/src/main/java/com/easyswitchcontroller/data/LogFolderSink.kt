package com.easyswitchcontroller.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 연결 로그가 나가는 바깥 출구 — 사용자가 고른 폴더에 로그 사본을 상시 남긴다.
 *
 * **왜 필요한가:** 안드로이드에서 구글 드라이브는 파일 선택기에 저장소처럼 붙는다(문서 제공자).
 * 사용자가 한 번 드라이브 폴더를 골라 두면 앱이 그리로 바로 쓰고, 그 파일이 **PC 쪽 드라이브
 * 폴더로 그대로 내려온다.** adb도, 수동 내보내기도 없이 증상이 난 시점의 로그를 곧장 볼 수 있다
 * (원격 근무 중이라 무선 디버깅을 못 켜는 상황에서 이게 유일한 경로였다).
 *
 * **덮어쓰기인 이유:** 하루 한 파일을 통째로 다시 쓴다. 줄 단위 append(`"wa"` 모드)는 문서
 * 제공자마다 지원이 갈려 드라이브에서 조용히 실패할 수 있다. 스냅샷 덮어쓰기는 어디서나 된다.
 */
object LogFolderSink {

    private const val PREFS = "easy_switch_log"
    private const val KEY_TREE = "log_folder_uri"
    private const val KEY_FILE_NAME = "log_file_name"
    private const val KEY_FILE_URI = "log_file_uri"
    private const val KEY_FILE_TREE = "log_file_tree"
    private val cacheLock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 사용자가 고른 폴더를 기억한다. 앱이 재시작해도 쓸 수 있도록 권한을 붙들어 둔다
     * ([Intent.FLAG_GRANT_WRITE_URI_PERMISSION] 영구화). 폴더가 바뀌면 파일 캐시는 버린다.
     */
    fun setFolder(context: Context, tree: Uri): Boolean {
        return try {
            context.applicationContext.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            synchronized(cacheLock) { prefs(context).edit()
                .putString(KEY_TREE, tree.toString())
                .remove(KEY_FILE_NAME)
                .remove(KEY_FILE_URI)
                .remove(KEY_FILE_TREE)
                .apply()
            }
            true
        } catch (e: Exception) {
            // 권한을 못 붙들면 다음 실행에서 못 쓴다 — 저장하지 않고 실패로 알린다.
            false
        }
    }

    /** 저장 폴더를 해제한다. 붙들어 둔 폴더 권한도 돌려준다 — 폴더에 이미 쓴 파일은 지우지 않는다. */
    fun clearFolder(context: Context) {
        val tree = getFolder(context)
        synchronized(cacheLock) { prefs(context).edit()
            .remove(KEY_TREE)
            .remove(KEY_FILE_NAME)
            .remove(KEY_FILE_URI)
            .remove(KEY_FILE_TREE)
            .apply()
        }
        tree?.let {
            runCatching {
                context.applicationContext.contentResolver.releasePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    fun getFolder(context: Context): Uri? =
        prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)

    /**
     * 설정 화면에 보일 값 — 폴더의 실제 이름과 그 폴더를 제공하는 앱(예: 드라이브)을 함께 보여 준다.
     *
     * 예전엔 트리 문서 ID의 끝을 잘라 보여 줬는데, ID 형식은 제공자마다 달라서 드라이브는 암호 같은
     * 문자열이 그대로 보였다(어느 폴더인지 알 수 없음). 제공자에게 표시 이름을 물어본다 — 제공자 조회라
     * 메인 스레드에서 부르지 않는다. 이름을 못 얻으면 예전 방식으로 대신한다.
     */
    fun folderLabel(context: Context): String? {
        val tree = getFolder(context) ?: return null
        val app = context.applicationContext
        val name = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            app.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: idLabel(tree)
        val provider = runCatching {
            app.packageManager.resolveContentProvider(tree.authority.orEmpty(), 0)
                ?.loadLabel(app.packageManager)?.toString()
        }.getOrNull()
        // 경로처럼 바깥(저장소 앱)부터 쓴다: "Drive/easy-switch"(사용자 확정).
        return if (provider.isNullOrBlank()) name else "$provider/$name"
    }

    private fun idLabel(tree: Uri): String = try {
        val id = DocumentsContract.getTreeDocumentId(tree)
        id.substringAfterLast(':').substringAfterLast('/').ifEmpty { tree.lastPathSegment.orEmpty() }
    } catch (e: Exception) {
        tree.lastPathSegment.orEmpty()
    }

    /**
     * 지정 폴더에 [fileName]을 통째로 덮어쓴다. 폴더가 없으면 아무 일도 하지 않는다.
     *
     * 같은 이름으로 [DocumentsContract.createDocument]를 다시 부르면 제공자가 "파일 (1).txt"처럼
     * 사본을 만들어 버리므로, 한 번 만든 문서 URI를 기억해 두고 재사용한다.
     */
    fun writeSnapshot(context: Context, fileName: String, text: String): Boolean {
        val app = context.applicationContext
        val tree = getFolder(app) ?: return false
        val resolver = app.contentResolver
        return try {
            val target = existingDocument(app, tree, fileName)
                ?: findDocument(app, tree, fileName)
                ?: createDocument(app, tree, fileName)
                ?: return false
            if (getFolder(app) != tree) return false
            // "wt" = 기존 내용을 잘라내고 새로 쓴다. 이게 없으면 짧아진 로그가 옛 꼬리를 남긴다.
            resolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: return false
            true
        } catch (e: Exception) {
            // 폴더 권한이 회수됐거나(사용자가 드라이브에서 지움) 오프라인일 수 있다.
            // 앱 내부 파일에는 그대로 남으니 조용히 실패한다.
            synchronized(cacheLock) {
                if (getFolder(app) == tree) {
                    prefs(app).edit().remove(KEY_FILE_NAME).remove(KEY_FILE_URI).remove(KEY_FILE_TREE).apply()
                }
            }
            false
        }
    }

    private fun existingDocument(context: Context, tree: Uri, fileName: String): Uri? = synchronized(cacheLock) {
        val p = prefs(context)
        // 0.2.14까지 만든 캐시에는 폴더 키가 없다. 폴더를 바꾸면 캐시를 지우므로 그때는 지금 폴더로 본다
        // — 이걸 불일치로 보면 업그레이드 직후 같은 이름 파일을 다시 만들어 "(1)" 사본이 생긴다(2026-10-17 실측).
        val cachedTree = p.getString(KEY_FILE_TREE, null)
        if (p.getString(KEY_FILE_NAME, null) != fileName || (cachedTree != null && cachedTree != tree.toString())) {
            return@synchronized null
        }
        p.getString(KEY_FILE_URI, null)?.let(Uri::parse)
    }

    /**
     * 캐시가 비었을 때(첫 실행·쓰기 실패 뒤 캐시 삭제) 폴더에 같은 이름 파일이 이미 있으면 그걸 쓴다.
     * 조회를 지원하지 않는 제공자라면 null을 돌려 새로 만드는 길로 넘긴다.
     */
    private fun findDocument(context: Context, tree: Uri, fileName: String): Uri? = runCatching {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val id = context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            var found: String? = null
            while (found == null && cursor.moveToNext()) {
                if (cursor.getString(1) == fileName) found = cursor.getString(0)
            }
            found
        } ?: return@runCatching null
        DocumentsContract.buildDocumentUriUsingTree(tree, id).also { remember(context, tree, fileName, it) }
    }.getOrNull()

    private fun createDocument(context: Context, tree: Uri, fileName: String): Uri? {
        val dir = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val created = DocumentsContract.createDocument(
            context.contentResolver,
            dir,
            "text/plain",
            fileName,
        ) ?: return null
        remember(context, tree, fileName, created)
        return created
    }

    private fun remember(context: Context, tree: Uri, fileName: String, document: Uri) {
        synchronized(cacheLock) {
            if (getFolder(context) == tree) prefs(context).edit()
                .putString(KEY_FILE_NAME, fileName)
                .putString(KEY_FILE_URI, document.toString())
                .putString(KEY_FILE_TREE, tree.toString())
                .apply()
        }
    }
}
