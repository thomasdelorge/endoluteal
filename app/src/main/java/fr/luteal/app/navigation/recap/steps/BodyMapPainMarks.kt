package fr.luteal.app.navigation.recap.steps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.luteal.core.designsystem.component.bodymap.BodyMap
import fr.luteal.core.designsystem.component.bodymap.BodyMapInteraction
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.recap.RecapPainMark
import fr.luteal.core.model.recap.toPainMark
import fr.luteal.core.model.recap.toRecapPainMark

@Composable
fun BodyMapPainMarks(
    marks: List<RecapPainMark>,
    onChange: (List<RecapPainMark>) -> Unit,
    modifier: Modifier = Modifier
) {
    val painMarks = marks.map(RecapPainMark::toPainMark)
    BodyMap(
        marks = painMarks,
        onMarksChange = { updated -> onChange(updated.map(PainMark::toRecapPainMark)) },
        modifier = modifier,
        interaction = BodyMapInteraction.Pins
    )
}
