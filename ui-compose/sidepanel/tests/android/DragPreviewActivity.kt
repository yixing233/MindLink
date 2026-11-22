package dev.mindlink.compose

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View

/** Debug-source-set fixture only. All data and touch events belong to this view. */
class DragPreviewActivity : Activity() {
    private lateinit var panel: View
    private val cards = arrayListOf<Bundle>()
    private var savedCards = emptyList<Bundle>()
    private val images = mutableMapOf<String, Bitmap>()
    private var editing = false
    private var downTime = 0L
    private var touchX = 180f
    private var touchY = 0f

    private fun bundle(vararg pairs: Pair<String, Any>) = Bundle().apply {
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Bundle -> putBundle(key, value)
            }
        }
    }

    private fun quote(id: String, text: String) = bundle(
        "id" to id, "cardType" to "hitokoto",
        "settings" to bundle("categories" to "a", "showSource" to true, "showAuthor" to false),
        "state" to bundle("kind" to "ready", "snapshot" to bundle(
            "uuid" to id, "text" to text, "source" to "Local fixture", "fetchedAt" to 1L,
        )),
    )

    private fun image(id: String, height: Int): Bundle {
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawColor(Color.rgb(226, 236, 238))
        val colors = listOf(Color.rgb(31, 134, 144), Color.rgb(221, 112, 91), Color.rgb(239, 196, 87))
        for (i in 0..7) {
            paint.color = colors[i % colors.size]
            canvas.drawRect(24f, 24f + i * 78, 456f, 72f + i * 78, paint)
        }
        paint.color = Color.BLACK
        paint.textSize = 36f
        canvas.drawText(id, 40f, 338f, paint)
        images[id] = bitmap
        return bundle("id" to id, "cardType" to "image", "imageAssetId" to id,
            "imageKey" to id, "heightDp" to height, "scaleMode" to "crop")
    }

    private fun snapshot() = bundle(
        "schemaVersion" to 1, "initialized" to true, "editing" to editing, "editorRoute" to "edit_home",
        "profile" to bundle("wxId" to "fixture", "nickname" to "Drag fixture",
            "status" to bundle("kind" to "no_status")),
    ).apply { putParcelableArrayList("cards", ArrayList(cards)) }

    private fun report(event: String) {
        Log.i("MindLinkDragFixture", "$event:${cards.map { it.getString("id") }} editing=$editing")
    }

    private fun callbacks(): Map<String, Runnable> = buildMap {
        put("enter_edit", Runnable {
            savedCards = cards.toList()
            editing = true
            refresh()
            report("ENTER_EDIT")
        })
        put("save_edit", Runnable {
            savedCards = cards.toList()
            editing = false
            refresh()
            report("SAVE")
        })
        put("discard_edit", Runnable {
            cards.clear()
            cards.addAll(savedCards)
            editing = false
            refresh()
            report("DISCARD")
        })
        put("gesture_start", Runnable { report("GESTURE_START") })
        put("gesture_end", Runnable { report("GESTURE_END") })
        cards.forEach { card ->
            val id = card.getString("id")!!
            cards.indices.forEach { index ->
                put("move_card:$id:$index", Runnable {
                    cards.add(index, cards.removeAt(cards.indexOfFirst { it.getString("id") == id }))
                    refresh()
                    report("MOVE")
                })
            }
        }
    }

    private fun refresh() = SidePanelUi.update(panel, snapshot(), images, callbacks())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        if (intent.getBooleanExtra("equal", false)) {
            for (i in 1..7) cards += quote("card$i", "Card $i")
        } else {
            cards += quote("short", "Short card")
            cards += image("image-tall", 360)
            cards += quote("multiline", "A taller text card.\nLine two.\nLine three.\nLine four.")
            cards += image("image-small", 120)
            cards += quote("tail-a", "Tail card A")
            cards += quote("tail-b", "Tail card B")
            cards += quote("tail-c", "Tail card C")
        }
        savedCards = cards.toList()
        panel = SidePanelUi.create(this, this, null, snapshot(), images, callbacks(), false)
        setContentView(panel)
        panel.post { report("READY") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        panel.post { handleIntent(intent) }
    }

    private fun handleIntent(intent: Intent) {
        touchX = intent.getIntExtra("x", touchX.toInt()).toFloat()
        touchY = intent.getIntExtra("y", touchY.toInt()).toFloat()
        when (intent.getStringExtra("touch")) {
            "down" -> {
                downTime = SystemClock.uptimeMillis()
                sendTouch(MotionEvent.ACTION_DOWN)
            }
            "move" -> sendTouch(MotionEvent.ACTION_MOVE)
            "up" -> sendTouch(MotionEvent.ACTION_UP)
            "cancel" -> sendTouch(MotionEvent.ACTION_CANCEL)
            // The move and final up happen within one main-loop turn, before layout can run.
            "fast-up" -> {
                sendTouch(MotionEvent.ACTION_MOVE)
                touchY = intent.getIntExtra("endY", touchY.toInt()).toFloat()
                sendTouch(MotionEvent.ACTION_UP)
            }
            "save" -> callbacks().getValue("save_edit").run()
            "discard" -> callbacks().getValue("discard_edit").run()
            else -> report("STATE")
        }
    }

    private fun sendTouch(action: Int) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, touchX, touchY, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try {
            val handled = panel.dispatchTouchEvent(event)
            Log.i("MindLinkDragFixture", "DISPATCH action=${event.actionMasked} x=$touchX y=$touchY handled=$handled")
        } finally {
            event.recycle()
        }
    }

    override fun onResume() {
        super.onResume()
        SidePanelUi.lifecycle(panel, true)
    }

    override fun onPause() {
        SidePanelUi.lifecycle(panel, false)
        super.onPause()
    }

    override fun onDestroy() {
        SidePanelUi.dispose(panel)
        super.onDestroy()
    }
}
