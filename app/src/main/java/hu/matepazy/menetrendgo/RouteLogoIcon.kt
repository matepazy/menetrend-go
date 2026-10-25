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

/** Paint the font outline, excluding advance width, bearings and baseline padding. */
@Composable internal fun RouteLogoIcon(logo: RouteLogo) {
    val context=LocalContext.current
    val outline=remember(context,logo.code) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=ResourcesCompat.getFont(context,R.font.mav_services)
            textSize=1000f
        }
        val glyph=Path().apply {paint.getTextPath(logo.glyph,0,logo.glyph.length,0f,0f,this)}
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
        Triple(glyph,exterior,bounds)
    }
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
