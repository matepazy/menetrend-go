package hu.matepazy.menetrendgo

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.PathParser
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** The same 48dp artwork and bundled mode pictograms as the Leaflet live map. */
internal class NativeMapArtwork(private val context:Context) {
    private val density=context.resources.displayMetrics.density
    private val fontScale=context.resources.configuration.fontScale
    private val cache=linkedMapOf<String,BitmapDescriptor>()
    companion object {
        val modeColors=mapOf("RAIL" to 0xff003a7e.toInt(),"COACH" to 0xfffdc300.toInt(),"BUS" to 0xff009ee3.toInt(),
            "SUBURBAN_RAILWAY" to 0xff005e3b.toInt(),"TRAMTRAIN" to 0xfff29101.toInt(),"RAIL_REPLACEMENT_BUS" to 0xffc4146e.toInt())
        private val resources=mapOf("RAIL" to R.drawable.ic_mode_rail,"COACH" to R.drawable.ic_mode_coach,
            "BUS" to R.drawable.ic_mode_local_bus,"SUBURBAN_RAILWAY" to R.drawable.ic_mode_hev,
            "TRAMTRAIN" to R.drawable.ic_mode_tramtrain,"RAIL_REPLACEMENT_BUS" to R.drawable.ic_mode_replacement_bus)
    }
    private data class Glyph(val width:Float,val paths:List<Pair<Path,Int>>)
    private val glyphs=resources.mapValues {(_,resource)->
        val parser=context.resources.getXml(resource)
        var width=32f
        val paths=mutableListOf<Pair<Path,Int>>()
        var first=true
        try {
            while(parser.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(parser.eventType==org.xmlpull.v1.XmlPullParser.START_TAG) {
                    val ns="http://schemas.android.com/apk/res/android"
                    if(parser.name=="vector")width=parser.getAttributeValue(ns,"viewportWidth").toFloat()
                    if(parser.name=="path") {
                        // The filter drawable's square background is replaced by the live map's circle.
                        if(first)first=false else {
                            val path=PathParser.createPathFromPathData(parser.getAttributeValue(ns,"pathData"))
                            path.fillType=Path.FillType.EVEN_ODD
                            paths.add(path to Color.parseColor(parser.getAttributeValue(ns,"fillColor")))
                        }
                    }
                }
                parser.next()
            }
        } finally {parser.close()}
        Glyph(width,paths)
    }
    private fun cached(key:String,create:()->Bitmap):BitmapDescriptor = cache.getOrPut(key) {
        val bitmap=create()
        BitmapDescriptorFactory.fromBitmap(bitmap).also {bitmap.recycle()}
    }.also {while(cache.size>256)cache.remove(cache.keys.first())}

    private fun canvas(width:Int=48,height:Int=48,draw:(Canvas,Paint)->Unit):Bitmap {
        val bitmap=Bitmap.createBitmap((width*density).toInt().coerceAtLeast(1),(height*density).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {scale(this@NativeMapArtwork.density,this@NativeMapArtwork.density);draw(this,Paint(Paint.ANTI_ALIAS_FLAG))}
        return bitmap
    }
    fun vehicle(mode:String,heading:Double?):BitmapDescriptor = cached("v:$mode:${heading?.toInt()}") {vehicleBitmap(mode,heading)}
    internal fun vehicleBitmap(mode:String,heading:Double?):Bitmap=canvas {c,p->
        c.translate(24f,24f)
        val color=modeColors[mode] ?: Color.BLACK
        val save=c.save()
        if(heading!=null)c.rotate(heading.toFloat())
        val shape=Path().apply {
            if(heading!=null) {
                moveTo(-6f,-13.75f);lineTo(0f,-22f);lineTo(6f,-13.75f)
                arcTo(RectF(-15f,-15f,15f,15f),-66.4f,312.8f,false);close()
            } else addCircle(0f,0f,15f,Path.Direction.CW)
        }
        p.color=color;p.setShadowLayer(2f,0f,2f,0x33112233)
        c.drawPath(shape,p);p.clearShadowLayer()
        p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.strokeJoin=Paint.Join.ROUND;p.color=Color.WHITE;c.drawPath(shape,p)
        p.style=Paint.Style.FILL
        c.restoreToCount(save)
        val glyph=glyphs[mode] ?: glyphs.getValue("RAIL")
        c.save()
        c.translate(-11.2f,-11.2f)
        c.scale(22.4f/glyph.width,22.4f/glyph.width)
        glyph.paths.forEach {(path,color)->p.color=color;c.drawPath(path,p)}
        c.restore()
    }
    fun cluster(count:Int):BitmapDescriptor=cached("c:$count") {canvas {c,p->
        p.color=0xff244c86.toInt();p.setShadowLayer(2f,0f,2f,0x33112233);c.drawCircle(24f,24f,17f,p);p.clearShadowLayer()
        p.color=Color.WHITE;p.style=Paint.Style.STROKE;p.strokeWidth=3f;c.drawCircle(24f,24f,17f,p);p.style=Paint.Style.FILL
        p.typeface=Typeface.DEFAULT_BOLD;p.textSize=13f*fontScale.coerceAtMost(1.3f);p.textAlign=Paint.Align.CENTER
        c.drawText(count.toString(),24f,24f-(p.ascent()+p.descent())/2,p)
    }}
    fun stop(modes:List<String>):BitmapDescriptor {
        val colors=modes.mapNotNull(modeColors::get).distinct().ifEmpty {listOf(0xff4e6581.toInt())}
        return cached("s:$colors") {canvas {c,p->
            p.color=Color.WHITE;p.setShadowLayer(2f,0f,2f,0x33112233);c.drawCircle(24f,24f,8f,p);p.clearShadowLayer()
            colors.forEachIndexed {i,color->p.color=color;c.drawArc(RectF(17f,17f,31f,31f),-90f+360f*i/colors.size,360f/colors.size,true,p)}
            p.color=0xfff8fbff.toInt();c.drawCircle(24f,24f,4f,p)
        }}
    }
    fun dot(radius:Float,fill:Int,stroke:Int,weight:Float):BitmapDescriptor=cached("dot:$radius:$fill:$stroke:$weight") {canvas {c,p->
        p.color=fill;c.drawCircle(24f,24f,radius,p);p.color=stroke;p.style=Paint.Style.STROKE;p.strokeWidth=weight;c.drawCircle(24f,24f,radius,p)
    }}
    fun label(name:String,ink:Int,surface:Int):BitmapDescriptor=cached("label:$name:$ink:$surface") {canvas(140,56) {c,_->
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=12*fontScale;typeface=Typeface.create("sans-serif",Typeface.BOLD);color=ink}
        val layout=StaticLayout.Builder.obtain(name,0,name.length,paint,140).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        paint.color=surface;paint.style=Paint.Style.STROKE;paint.strokeJoin=Paint.Join.ROUND;paint.strokeWidth=3f;layout.draw(c)
        paint.color=ink;paint.style=Paint.Style.FILL;layout.draw(c)
    }}
}
