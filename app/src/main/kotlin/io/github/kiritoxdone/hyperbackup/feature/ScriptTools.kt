package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*

@Composable fun ScriptToolsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    PageList(padding) {
        notices(s, vm)
        item { Card(Modifier.fillMaxWidth()) {
            ToolGroup.entries.forEach { group ->
                ArrowPreference(group.title, startAction = { LeadingIcon(group.icon()) }, onClick = { navigate(Route.ToolsInGroup(group)) })
            }
        } }
    }
}
@Composable fun ToolGroupPage(s: AppState, vm: AppViewModel, padding: PaddingValues, group: ToolGroup, navigate: (Route) -> Unit) {
    PageList(padding) {
        notices(s, vm)
        item { Card(Modifier.fillMaxWidth()) {
            ScriptCommand.entries.filter { it.group() == group }.forEach { command ->
                ArrowPreference(command.label, onClick = {
                    vm.beginScript(command)
                    if (vm.state.value.error == null && vm.state.value.draft?.script?.command == command) navigate(Route.Config)
                })
            }
        } }
    }
}

@Composable fun PromptCard(prompt: EnginePrompt, busy: Boolean, answer: (String) -> Unit) {
    var selected by rememberSaveable(prompt.id) { mutableStateOf(emptyList<Int>()) }
    var text by rememberSaveable(prompt.id) { mutableStateOf("") }
    Card(insideMargin = PaddingValues(16.dp)) {
        Text(prompt.title)
        if (prompt.mode == "input") {
            TextField(text, { text = it }, Modifier.fillMaxWidth(), label = "输入内容")
            TextButton("确认", { answer(text) }, enabled = !busy)
        } else {
            prompt.options.forEachIndexed { index, label ->
                if (prompt.mode == "multi") CheckboxPreference(title = label, checked = index + 1 in selected, onCheckedChange = { checked -> selected = if (checked) selected + (index + 1) else selected - (index + 1) })
                else TextButton(label, { answer((index + 1).toString()) }, enabled = !busy)
            }
            if (prompt.mode == "multi") TextButton("确认所选项", { answer(selected.joinToString(" ")) }, enabled = !busy && selected.isNotEmpty())
        }
        TextButton(prompt.cancel, { answer("0") }, enabled = !busy)
    }
}
