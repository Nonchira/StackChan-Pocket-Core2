package jp.stackchan.pocket

import android.graphics.*
import android.graphics.drawable.Drawable

/** DOS-style double rules, drawn at view bounds so Japanese text never breaks a character grid. */
class TerminalFrame(private val fill:Int,private val rule:Int,private val density:Float):Drawable() {
    private val paint=Paint().apply { isAntiAlias=false }
    override fun draw(canvas:Canvas) {
        val b=bounds
        paint.style=Paint.Style.FILL;paint.color=fill;canvas.drawRect(b,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=density.coerceAtLeast(1f);paint.color=rule
        val edge=paint.strokeWidth/2
        canvas.drawRect(b.left+edge,b.top+edge,b.right-edge,b.bottom-edge,paint)
        val inset=3*density+edge
        if(b.width()>inset*2 && b.height()>inset*2)
            canvas.drawRect(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset,paint)
    }
    override fun setAlpha(alpha:Int) { paint.alpha=alpha;invalidateSelf() }
    override fun setColorFilter(filter:ColorFilter?) { paint.colorFilter=filter;invalidateSelf() }
    @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.OPAQUE
}
