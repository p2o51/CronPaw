package com.gpo.cronpaw.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    secret: Boolean = false,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    TextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
internal fun Row2(title: String, value: String? = null, onClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(title, Modifier.weight(1f).padding(start = if (leading != null) 12.dp else 0.dp), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One-of choices as tonal pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Text(
                label,
                Modifier
                    .clip(CircleShape)
                    .background(if (on) cs.primary else cs.surfaceContainerHighest)
                    .clickable { onSelect(v) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) cs.onPrimary else cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
