package hu.matepazy.menetrendgo

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat

internal fun routeLogoOutline(context: android.content.Context, code: Int): Triple<Path, Path, RectF> {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface=ResourcesCompat.getFont(context,R.font.mav_services)
        textSize=1000f
    }
    val text = String(Character.toChars(code))
    val glyph=Path().apply {paint.getTextPath(text,0,text.length,0f,0f,this)}
    val bounds=RectF().also {glyph.computeBounds(it,true)}
    // The largest contour is the mark's exterior. Filling this contour preserves
    // its exact corners while supplying the background beneath letter cutouts.
    val measure=PathMeasure(glyph,false)
    var exterior=Path()
    var largestArea=0f
    do {
        val contour=Path().apply {measure.getSegment(0f,measure.length,this,true);close()}
        val rect=RectF().also {contour.computeBounds(it,true)}
        val area=rect.width()*rect.height()
        if(area>largestArea) {largestArea=area;exterior=contour}
    } while(measure.nextContour())
    return Triple(glyph,exterior,bounds)
}

/** Paint the font outline, excluding advance width, bearings and baseline padding. */
@Composable internal fun RouteLogoIcon(logo: RouteLogo) {
    val context=LocalContext.current
    val outline=remember(context,logo.code) { routeLogoOutline(context, logo.code) }
    val (glyph,exterior,bounds)=outline
    if(bounds.isEmpty) return
    val foreground=remember(logo.foreground) {Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=android.graphics.Color.parseColor("#${logo.foreground}")
    }}
    val background=remember(logo.background) {Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=android.graphics.Color.parseColor("#${logo.background}")
    }}
    Canvas(Modifier.size(width=(20f*bounds.width()/bounds.height()).dp,height=20.dp).clearAndSetSemantics {}) {
        drawIntoCanvas {canvas ->
            val native=canvas.nativeCanvas
            native.save()
            native.scale(size.width/bounds.width(),size.height/bounds.height())
            native.translate(-bounds.left,-bounds.top)
            native.drawPath(exterior,background)
            native.drawPath(glyph,foreground)
            native.restore()
        }
    }
}

/** RemoteViews uses the same bundled glyph outline and official color pair as Compose. */
internal fun routeLogoBitmap(context: android.content.Context, logo: RouteLogo): android.graphics.Bitmap? {
    val (glyph, exterior, bounds) = routeLogoOutline(context, logo.code)
    if (bounds.isEmpty) return null
    val height = (20 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val width = (height * bounds.width() / bounds.height()).toInt().coerceIn(1, height * 8)
    return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(width / bounds.width(), height / bounds.height())
        canvas.translate(-bounds.left, -bounds.top)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.parseColor("#${logo.background}")
        canvas.drawPath(exterior, paint)
        paint.color = android.graphics.Color.parseColor("#${logo.foreground}")
        canvas.drawPath(glyph, paint)
    }
}
