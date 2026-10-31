package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal val countyPassIcons = mapOf(
    "Baranya" to R.drawable.pass_county_baranya,
    "Bács-Kiskun" to R.drawable.pass_county_bk,
    "Békés" to R.drawable.pass_county_bekes,
    "Borsod-Abaúj-Zemplén" to R.drawable.pass_county_baz,
    "Csongrád-Csanád" to R.drawable.pass_county_csongrad,
    "Fejér" to R.drawable.pass_county_fejer,
    "Győr-Moson-Sopron" to R.drawable.pass_county_gyms,
    "Hajdú-Bihar" to R.drawable.pass_county_hb,
    "Heves" to R.drawable.pass_county_heves,
    "Jász-Nagykun-Szolnok" to R.drawable.pass_county_jnsz,
    "Komárom-Esztergom" to R.drawable.pass_county_ke,
    "Nógrád" to R.drawable.pass_county_nograd,
    "Pest" to R.drawable.pass_county_pest,
    "Somogy" to R.drawable.pass_county_somogy,
    "Szabolcs-Szatmár-Bereg" to R.drawable.pass_county_szszb,
    "Tolna" to R.drawable.pass_county_tolna,
    "Vas" to R.drawable.pass_county_vas,
    "Veszprém" to R.drawable.pass_county_veszprem,
    "Zala" to R.drawable.pass_county_zala,
)

@Composable internal fun PassIcon(pass: Pass) {
    val modifier = Modifier.size(40.dp).clearAndSetSemantics {}
    val color = MaterialTheme.colorScheme.onSurface
    val countyIcon = countyPassIcons[pass.county]
    when {
        pass.localCity != null -> Icon(Icons.Outlined.LocationCity, null, modifier, tint = color)
        countyIcon != null -> Icon(painterResource(countyIcon), null, modifier, tint = color)
        pass.county == null -> Icon(painterResource(R.drawable.pass_hungary), null, modifier, tint = color)
        else -> Icon(Icons.Outlined.Map, null, modifier, tint = color)
    }
}
