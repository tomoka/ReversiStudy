package mobi.tomo.reversi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import mobi.tomo.reversi.game.Board
import mobi.tomo.reversi.game.ComputerPlayer
import mobi.tomo.reversi.game.Disc
import mobi.tomo.reversi.game.Game
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

/**
 * 盤面の描画とタッチ入力を担当する View。
 *
 * ルールと進行は [Game] / [Board]、CPU の思考は [ComputerPlayer] が持つ。
 * この View は「今の局面を描く」「タップをマスに変換して [Game] に渡す」
 * 「CPU の手番になったら考えさせる」ことだけを行う。
 *
 * マスの大きさは View の実寸から算出するため、画面の密度やサイズに依存しない。
 * 画面が回転して Activity が作り直されても、[onSaveInstanceState] で局面を保存し
 * 復元する。
 */
class ReversiView(context: Context) : View(context) {

    /** 対局中の状態。null はタイトル画面。 */
    private var game: Game? = null

    /** プレイヤーの色。もう一方を CPU が持つ。 */
    private var playerDisc = Disc.BLACK

    private val computer = ComputerPlayer()

    /** 盤の一辺（px）。0 のうちはまだ採寸できていない。 */
    private var boardSide = 0f
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var statusBaseline = 0f
    private var scoreBaseline = 0f
    private var footerBaseline = 0f

    private val blackButton = RectF()
    private val whiteButton = RectF()
    private val passBox = RectF()

    /** 裏返るアニメーションの開始遅延（ms）。[NO_DELAY] はアニメーションしないマス。 */
    private val flipDelay = IntArray(100) { NO_DELAY }
    private var placedIndex = NO_INDEX
    private var animationStart = 0L
    private var animationEnd = 0L

    /** 端末の「アニメーションを無効」設定を尊重する。0 ならアニメーションしない。 */
    private val animationScale: Float = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f).coerceIn(0f, 4f)

    /** 石の半径。onSizeChanged で決まる。 */
    private var stoneRadius = 0f

    private val boardPaint = fillPaint(R.color.board_green)
    private val hintPaint = fillPaint(R.color.hint)

    /** 石の表面。左上からの光を放射グラデーションで表す。寸法が決まってから作る。 */
    private val blackFacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whiteFacePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 石の側面（厚み）。真横を向いたときに見える。 */
    private val blackSidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whiteSidePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 盤に落ちる影。 */
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 盤の四隅を少し暗くして、盤面に奥行きを出す。 */
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 格子の溝の下側に入れる明るい線。彫り込んで見せるため。 */
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_bevel)
        style = Paint.Style.STROKE
    }
    private val scrimPaint = fillPaint(R.color.scrim)
    private val overlayPaint = fillPaint(R.color.overlay)
    private val buttonPaint = fillPaint(R.color.button_fill)
    private val starPaint = fillPaint(R.color.board_line)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_line)
        style = Paint.Style.STROKE
    }
    private val buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.button_stroke)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val discEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_line)
        style = Paint.Style.STROKE
    }

    private val titlePaint = textPaint(R.color.text_primary, sp(30f))
    private val bodyPaint = textPaint(R.color.text_primary, sp(18f))
    private val labelPaint = textPaint(R.color.text_secondary, sp(14f))

    /** パス表示を一定時間見せてから手番を戻す。 */
    private val passRunnable = Runnable {
        game?.acknowledgePass()
        scheduleNext()
        invalidate()
    }

    init {
        // 状態の保存・復元は id のある View だけが対象になる
        id = R.id.reversi_view
    }

    /** CPU に一手指させる。少し待ってから動かすことで、直前の着手が見えるようにする。 */
    private val computerRunnable = Runnable {
        val current = game
        if (current != null && current.state == Game.State.IN_PROGRESS && current.turn != playerDisc) {
            computer.chooseMove(current.board, current.turn)?.let { playWithAnimation(current, it) }
        }
        scheduleNext()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val availableWidth = (w - paddingLeft - paddingRight).toFloat()
        val availableHeight = (h - paddingTop - paddingBottom).toFloat()
        // 余白は画面に対する割合で頭打ちにする。小さな画面でも盤が必ず残るようにするため。
        val header = min(dp(76f), availableHeight * 0.14f)
        val footer = min(dp(40f), availableHeight * 0.07f)
        val margin = min(dp(12f), availableWidth * 0.03f)

        boardSide = max(
            0f,
            min(availableWidth - margin * 2f, availableHeight - header - footer - margin * 2f),
        )
        cell = boardSide / Board.SIZE
        boardLeft = paddingLeft + (availableWidth - boardSide) / 2f
        boardTop = paddingTop + header +
            max(0f, (availableHeight - header - footer - boardSide) / 2f)

        statusBaseline = boardTop - header * 0.45f
        scoreBaseline = boardTop - header * 0.14f
        footerBaseline = boardTop + boardSide + footer * 0.7f

        stoneRadius = cell * 0.4f
        buildStoneShaders()

        linePaint.strokeWidth = max(dp(1f), cell * 0.02f)
        discEdgePaint.strokeWidth = max(dp(0.5f), cell * 0.02f)
        bevelPaint.strokeWidth = max(dp(1f), cell * 0.015f)

        // 文字は sp を基本にしつつ、盤より大きくならないよう頭打ちにする。
        titlePaint.textSize = min(sp(30f), boardSide * 0.11f)
        bodyPaint.textSize = min(sp(18f), boardSide * 0.075f)
        labelPaint.textSize = min(sp(14f), boardSide * 0.055f)

        val buttonHeight = boardSide * 0.14f
        val buttonTop = boardTop + boardSide * 0.58f
        blackButton.set(
            boardLeft + boardSide * 0.06f,
            buttonTop,
            boardLeft + boardSide * 0.47f,
            buttonTop + buttonHeight,
        )
        whiteButton.set(
            boardLeft + boardSide * 0.53f,
            buttonTop,
            boardLeft + boardSide * 0.94f,
            buttonTop + buttonHeight,
        )
        val passHeight = boardSide * 0.2f
        passBox.set(
            boardLeft + boardSide * 0.2f,
            boardTop + (boardSide - passHeight) / 2f,
            boardLeft + boardSide * 0.8f,
            boardTop + (boardSide + passHeight) / 2f,
        )
    }

    /**
     * 石と盤の陰影を作る。原点を石の中心とした座標で作っておき、
     * 描くときに Canvas を平行移動して使い回す。
     */
    private fun buildStoneShaders() {
        if (stoneRadius <= 0f) return
        val r = stoneRadius
        // 光源は左上。ハイライトをその方向へ寄せる
        val lightX = -r * 0.38f
        val lightY = -r * 0.38f

        blackFacePaint.shader = RadialGradient(
            lightX, lightY, r * 1.7f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.disc_black_lit),
                ContextCompat.getColor(context, R.color.disc_black),
                ContextCompat.getColor(context, R.color.disc_black_shade),
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        whiteFacePaint.shader = RadialGradient(
            lightX, lightY, r * 1.7f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.disc_white_lit),
                ContextCompat.getColor(context, R.color.disc_white),
                ContextCompat.getColor(context, R.color.disc_white_shade),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        // 側面は円筒なので、上が明るく下が暗い縦のグラデーション
        blackSidePaint.shader = LinearGradient(
            0f, -r, 0f, r,
            ContextCompat.getColor(context, R.color.disc_black_side),
            ContextCompat.getColor(context, R.color.disc_black_shade),
            Shader.TileMode.CLAMP,
        )
        whiteSidePaint.shader = LinearGradient(
            0f, -r, 0f, r,
            ContextCompat.getColor(context, R.color.disc_white_side),
            ContextCompat.getColor(context, R.color.disc_white_shade),
            Shader.TileMode.CLAMP,
        )
        // 接地影は中心が濃く外へ向かって消える
        shadowPaint.shader = RadialGradient(
            0f, 0f, r * 1.15f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.stone_shadow),
                ContextCompat.getColor(context, R.color.stone_shadow),
                ContextCompat.getColor(context, R.color.transparent),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        vignettePaint.shader = RadialGradient(
            boardLeft + boardSide / 2f, boardTop + boardSide / 2f, boardSide * 0.72f,
            intArrayOf(
                ContextCompat.getColor(context, R.color.transparent),
                ContextCompat.getColor(context, R.color.transparent),
                ContextCompat.getColor(context, R.color.board_vignette),
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun facePaint(disc: Disc) = if (disc == Disc.BLACK) blackFacePaint else whiteFacePaint

    private fun sidePaint(disc: Disc) = if (disc == Disc.BLACK) blackSidePaint else whiteSidePaint

    override fun onDraw(canvas: Canvas) {
        if (boardSide <= 0f) return

        drawBoard(canvas)
        val current = game
        if (current == null) {
            drawTitle(canvas)
            return
        }

        drawStones(canvas, current)
        // 裏返っている最中はパスや結果を重ねない。石の動きが隠れてしまうため
        val animating = isAnimating()
        when (current.state) {
            Game.State.IN_PROGRESS -> {
                if (current.turn == playerDisc && !animating) drawHints(canvas, current)
                drawStatus(canvas, current)
            }
            Game.State.PASS -> {
                drawStatus(canvas, current)
                if (!animating) drawPass(canvas)
            }
            Game.State.FINISHED -> if (!animating) drawResult(canvas, current)
        }

        if (animating) {
            postInvalidateOnAnimation()
        } else if (animationEnd > 0L) {
            clearAnimation()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)

        val current = game
        if (current == null) {
            when {
                blackButton.contains(event.x, event.y) -> startGame(Disc.BLACK)
                whiteButton.contains(event.x, event.y) -> startGame(Disc.WHITE)
            }
            return true
        }

        when (current.state) {
            Game.State.IN_PROGRESS -> {
                // CPU の手番中はタップを受け付けない
                if (current.turn != playerDisc) return true
                val index = indexAt(event.x, event.y)
                if (index in current.legalMoves) {
                    playWithAnimation(current, index)
                    scheduleNext()
                    invalidate()
                }
            }
            Game.State.PASS -> Unit
            Game.State.FINISHED -> backToTitle()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        cancelPending()
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable(KEY_SUPER, super.onSaveInstanceState())
        state.putString(KEY_PLAYER_DISC, playerDisc.name)
        game?.let {
            state.putString(KEY_BOARD, it.board.toDiagram())
            state.putString(KEY_TURN, it.turn.name)
        }
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }

        playerDisc = discOf(state.getString(KEY_PLAYER_DISC), Disc.BLACK)
        val diagram = state.getString(KEY_BOARD)
        val turn = state.getString(KEY_TURN)
        game = if (diagram != null && turn != null) {
            Game(
                first = discOf(turn, Disc.BLACK),
                board = Board.fromDiagram(*diagram.split("\n").toTypedArray()),
            )
        } else {
            null
        }

        clearAnimation()
        super.onRestoreInstanceState(
            BundleCompat.getParcelable(state, KEY_SUPER, Parcelable::class.java),
        )
        // 復元した局面が CPU の手番やパスなら、その続きを予約し直す
        scheduleNext()
    }

    private fun discOf(name: String?, fallback: Disc): Disc =
        Disc.entries.firstOrNull { it.name == name } ?: fallback

    private fun startGame(disc: Disc) {
        playerDisc = disc
        game = Game()
        clearAnimation()
        scheduleNext()
        invalidate()
    }

    /** 着手し、裏返る石のアニメーションを仕込む。 */
    private fun playWithAnimation(game: Game, index: Int) {
        val before = game.board
        game.play(index)
        startFlipAnimation(before, game.board, index)
    }

    private fun startFlipAnimation(before: Board, after: Board, placed: Int) {
        clearAnimation()
        if (animationScale <= 0f) return

        val flipMillis = FLIP_MILLIS * animationScale
        val stagger = STAGGER_MILLIS * animationScale
        var lastDelay = 0f
        for (index in Board.INDICES) {
            val old = before.discAt(index) ?: continue
            if (after.discAt(index) == old) continue
            // 置いたマスから遠い石ほど遅れて返る。波が広がるように見せるため
            val distance = max(
                abs(Board.colOf(index) - Board.colOf(placed)),
                abs(Board.rowOf(index) - Board.rowOf(placed)),
            )
            val delay = (distance - 1).coerceAtLeast(0) * stagger
            flipDelay[index] = delay.toInt()
            lastDelay = max(lastDelay, delay)
        }

        placedIndex = placed
        animationStart = AnimationUtils.currentAnimationTimeMillis()
        animationEnd = animationStart + (lastDelay + flipMillis).toLong()
    }

    private fun clearAnimation() {
        flipDelay.fill(NO_DELAY)
        placedIndex = NO_INDEX
        animationStart = 0L
        animationEnd = 0L
    }

    private fun isAnimating(): Boolean =
        animationEnd > 0L && AnimationUtils.currentAnimationTimeMillis() < animationEnd

    private fun backToTitle() {
        cancelPending()
        clearAnimation()
        game = null
        invalidate()
    }

    /** 局面が進んだあと、自動で動かすものがあれば予約する。 */
    private fun scheduleNext() {
        cancelPending()
        val current = game ?: return
        when (current.state) {
            Game.State.PASS -> postDelayed(passRunnable, PASS_DISPLAY_MILLIS)
            Game.State.IN_PROGRESS ->
                if (current.turn != playerDisc) postDelayed(computerRunnable, THINKING_MILLIS)
            Game.State.FINISHED -> Unit
        }
    }

    private fun cancelPending() {
        removeCallbacks(passRunnable)
        removeCallbacks(computerRunnable)
    }

    // ------------------------------------------------------------------ 描画

    private fun drawBoard(canvas: Canvas) {
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide, boardPaint)
        // 溝の線と、その右下に入れる明るい線。2 本 1 組で彫り込んだように見せる
        val bevel = max(1f, cell * 0.015f)
        for (i in 0..Board.SIZE) {
            val x = boardLeft + cell * i
            canvas.drawLine(x, boardTop, x, boardTop + boardSide, linePaint)
            canvas.drawLine(x + bevel, boardTop, x + bevel, boardTop + boardSide, bevelPaint)
            val y = boardTop + cell * i
            canvas.drawLine(boardLeft, y, boardLeft + boardSide, y, linePaint)
            canvas.drawLine(boardLeft, y + bevel, boardLeft + boardSide, y + bevel, bevelPaint)
        }
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide, vignettePaint)
        for (col in intArrayOf(2, 6)) {
            for (row in intArrayOf(2, 6)) {
                canvas.drawCircle(
                    boardLeft + cell * col,
                    boardTop + cell * row,
                    cell * 0.07f,
                    starPaint,
                )
            }
        }
    }

    private fun drawStones(canvas: Canvas, game: Game) {
        val radius = cell * 0.4f
        val thickness = radius * THICKNESS_RATIO
        val now = AnimationUtils.currentAnimationTimeMillis()
        val flipMillis = FLIP_MILLIS * animationScale
        val placeMillis = PLACE_MILLIS * animationScale

        for (index in Board.INDICES) {
            val disc = game.board.discAt(index) ?: continue
            val x = centerX(index)
            val y = centerY(index)
            val delay = flipDelay[index]

            when {
                // 裏返り中：横幅を縮めて 0 を通り、半分を過ぎたら色が変わる。
                // 真横を向いたところで厚みが見え、少し浮き上がる
                delay != NO_DELAY && animationEnd > 0L -> {
                    val progress = progress(now - animationStart - delay, flipMillis)
                    val angle = Math.PI * progress
                    val shown = if (progress < 0.5f) disc.opposite else disc
                    val halfWidth = radius * abs(cos(angle)).toFloat()
                    val sideHalfWidth = halfWidth + thickness * abs(sin(angle)).toFloat()
                    val lift = radius * LIFT_RATIO * abs(sin(angle)).toFloat()
                    drawStone(canvas, x, y, halfWidth, radius, sideHalfWidth, lift, shown)
                }
                // 置いた石：小さく現れる
                index == placedIndex && animationEnd > 0L -> {
                    val progress = progress(now - animationStart, placeMillis)
                    val eased = progress * progress * (3f - 2f * progress)
                    val size = radius * (0.3f + 0.7f * eased)
                    drawStone(canvas, x, y, size, size, size, 0f, disc)
                }
                else -> drawStone(canvas, x, y, radius, radius, radius, 0f, disc)
            }
        }
    }

    /**
     * 石を 1 枚描く。
     *
     * @param halfWidth 表面の横半径。裏返る途中は 0 に近づく
     * @param sideHalfWidth 厚みまで含めた見かけの横半径。真横を向くと厚みだけが残る
     * @param lift 盤から浮かせる量。転がっているように見せる
     */
    private fun drawStone(
        canvas: Canvas,
        x: Float,
        y: Float,
        halfWidth: Float,
        halfHeight: Float,
        sideHalfWidth: Float,
        lift: Float,
        disc: Disc,
    ) {
        canvas.save()
        canvas.translate(x, y)

        // 接地影。石が細くなれば影も細く、浮けば薄くなる
        canvas.save()
        canvas.translate(stoneRadius * 0.10f, stoneRadius * 0.14f)
        canvas.scale(max(0.12f, sideHalfWidth / stoneRadius), 1f)
        shadowPaint.alpha = (255 * (1f - 0.45f * (lift / stoneRadius).coerceIn(0f, 1f))).toInt()
        canvas.drawCircle(0f, 0f, stoneRadius * 1.05f, shadowPaint)
        canvas.restore()
        shadowPaint.alpha = 255

        canvas.translate(0f, -lift)

        // 厚み（側面）。表面より広いときだけ見える
        if (sideHalfWidth > halfWidth + 0.5f) {
            val corner = min(sideHalfWidth, halfHeight)
            canvas.drawRoundRect(
                -sideHalfWidth, -halfHeight, sideHalfWidth, halfHeight,
                corner, corner, sidePaint(disc),
            )
        }

        // 表面。Canvas ごと横に縮めることで、ハイライトも一緒に潰れる
        if (halfWidth > 0.5f) {
            canvas.save()
            canvas.scale(halfWidth / halfHeight, 1f)
            canvas.drawCircle(0f, 0f, halfHeight, facePaint(disc))
            if (halfWidth > halfHeight * 0.25f) {
                canvas.drawCircle(0f, 0f, halfHeight, discEdgePaint)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    /** 経過時間を 0..1 に。まだ始まっていなければ 0、終わっていれば 1。 */
    private fun progress(elapsed: Long, duration: Float): Float =
        if (duration <= 0f) 1f else (elapsed / duration).coerceIn(0f, 1f)

    private fun drawHints(canvas: Canvas, game: Game) {
        for (index in game.legalMoves) {
            canvas.drawCircle(centerX(index), centerY(index), cell * 0.13f, hintPaint)
        }
    }

    private fun drawStatus(canvas: Canvas, game: Game) {
        val centerX = boardLeft + boardSide / 2f
        val turnText = if (game.turn == Disc.BLACK) R.string.turn_black else R.string.turn_white
        canvas.drawText(context.getString(turnText), centerX, statusBaseline, bodyPaint)
        canvas.drawText(
            context.getString(R.string.score, game.count(Disc.BLACK), game.count(Disc.WHITE)),
            centerX,
            scoreBaseline,
            labelPaint,
        )
        val footerText = when {
            game.state == Game.State.IN_PROGRESS && game.turn != playerDisc -> R.string.thinking
            playerDisc == Disc.BLACK -> R.string.your_side_black
            else -> R.string.your_side_white
        }
        canvas.drawText(context.getString(footerText), centerX, footerBaseline, labelPaint)
    }

    private fun drawTitle(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        val centerX = boardLeft + boardSide / 2f
        canvas.drawText(
            context.getString(R.string.app_name),
            centerX,
            boardTop + boardSide * 0.3f,
            titlePaint,
        )
        canvas.drawText(
            context.getString(R.string.choose_side),
            centerX,
            boardTop + boardSide * 0.44f,
            bodyPaint,
        )
        drawChoiceButton(canvas, blackButton, Disc.BLACK, context.getString(R.string.choose_black))
        drawChoiceButton(canvas, whiteButton, Disc.WHITE, context.getString(R.string.choose_white))
    }

    private fun drawChoiceButton(canvas: Canvas, rect: RectF, disc: Disc, label: String) {
        val corner = rect.height() / 4f
        canvas.drawRoundRect(rect, corner, corner, buttonPaint)
        canvas.drawRoundRect(rect, corner, corner, buttonStrokePaint)

        val discX = rect.left + rect.height() * 0.5f
        val discRadius = rect.height() * 0.28f
        canvas.save()
        canvas.translate(discX, rect.centerY())
        canvas.scale(discRadius / stoneRadius, discRadius / stoneRadius)
        canvas.drawCircle(0f, 0f, stoneRadius, facePaint(disc))
        canvas.drawCircle(0f, 0f, stoneRadius, discEdgePaint)
        canvas.restore()
        canvas.drawText(
            label,
            (discX + rect.right) / 2f,
            rect.centerY() + labelPaint.textSize * 0.36f,
            labelPaint,
        )
    }

    private fun drawPass(canvas: Canvas) {
        val corner = passBox.height() / 4f
        canvas.drawRoundRect(passBox, corner, corner, overlayPaint)
        canvas.drawText(
            context.getString(R.string.pass),
            passBox.centerX(),
            passBox.centerY() + titlePaint.textSize * 0.36f,
            titlePaint,
        )
    }

    private fun drawResult(canvas: Canvas, game: Game) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        val centerX = boardLeft + boardSide / 2f
        canvas.drawText(
            context.getString(R.string.result),
            centerX,
            boardTop + boardSide * 0.26f,
            titlePaint,
        )
        canvas.drawText(
            context.getString(R.string.black_count, game.count(Disc.BLACK)),
            centerX,
            boardTop + boardSide * 0.42f,
            bodyPaint,
        )
        canvas.drawText(
            context.getString(R.string.white_count, game.count(Disc.WHITE)),
            centerX,
            boardTop + boardSide * 0.52f,
            bodyPaint,
        )
        val winner = when (game.winner) {
            Disc.BLACK -> R.string.winner_black
            Disc.WHITE -> R.string.winner_white
            null -> R.string.draw
        }
        canvas.drawText(
            context.getString(winner),
            centerX,
            boardTop + boardSide * 0.68f,
            titlePaint,
        )
        canvas.drawText(
            context.getString(R.string.tap_to_restart),
            centerX,
            boardTop + boardSide * 0.82f,
            labelPaint,
        )
    }

    // ------------------------------------------------------------ 座標の変換

    private fun centerX(index: Int) = boardLeft + cell * (Board.colOf(index) - 0.5f)

    private fun centerY(index: Int) = boardTop + cell * (Board.rowOf(index) - 0.5f)

    /** タッチ座標を盤上のマス番号へ。盤の外なら [NO_INDEX]。 */
    private fun indexAt(x: Float, y: Float): Int {
        if (cell <= 0f || x < boardLeft || y < boardTop) return NO_INDEX
        val col = ((x - boardLeft) / cell).toInt() + 1
        val row = ((y - boardTop) / cell).toInt() + 1
        if (col !in 1..Board.SIZE || row !in 1..Board.SIZE) return NO_INDEX
        return Board.index(col, row)
    }

    // -------------------------------------------------------------- 小道具

    private fun fillPaint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        style = Paint.Style.FILL
    }

    private fun textPaint(colorRes: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        textSize = size
        textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val NO_INDEX = -1
        const val NO_DELAY = -1
        const val PASS_DISPLAY_MILLIS = 1200L

        /** 石 1 枚が裏返りきるまでの時間。 */
        const val FLIP_MILLIS = 240f

        /** 置いたマスから 1 マス離れるごとに遅らせる時間。 */
        const val STAGGER_MILLIS = 30f

        /** 置いた石が現れるまでの時間。 */
        const val PLACE_MILLIS = 160f

        /** 石の厚み。半径に対する割合。 */
        const val THICKNESS_RATIO = 0.26f

        /** 裏返る途中で盤から浮かせる量。半径に対する割合。 */
        const val LIFT_RATIO = 0.16f

        /** CPU が考えているように見せる待ち時間。探索自体は数十ミリ秒で終わる。 */
        const val THINKING_MILLIS = 600L

        const val KEY_SUPER = "super"
        const val KEY_BOARD = "board"
        const val KEY_TURN = "turn"
        const val KEY_PLAYER_DISC = "playerDisc"
    }
}
