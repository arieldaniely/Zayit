package io.github.kdroidfilter.seforimapp.luach

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.seforimapp.siddur.IsraeliOccasion
import org.jetbrains.compose.resources.stringResource
import seforimapp.luach.generated.resources.Res
import seforimapp.luach.generated.resources.luach_yom_haatzmaut
import seforimapp.luach.generated.resources.luach_yom_hazikaron
import seforimapp.luach.generated.resources.luach_yom_yerushalayim

@Composable
fun IsraeliOccasion.localizedName(): String =
    stringResource(
        when (this) {
            IsraeliOccasion.YOM_HAZIKARON -> Res.string.luach_yom_hazikaron
            IsraeliOccasion.YOM_HAATZMAUT -> Res.string.luach_yom_haatzmaut
            IsraeliOccasion.YOM_YERUSHALAYIM -> Res.string.luach_yom_yerushalayim
        },
    )
