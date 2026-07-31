package com.example.shoppinglist

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.shoppinglist.ui.theme.ShoppingListTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoppingListTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val factory = ShoppingViewModelFactory(LocalContext.current.applicationContext as Application)
    val viewModel: ShoppingViewModel = viewModel(factory = factory)

    Scaffold(
        bottomBar = { AppBottomNavigation(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.AllItems.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.AllItems.route) {
                ShoppingListScreen(viewModel, viewModel.shoppingList)
            }
            composable(Screen.PendingItems.route) {
                ShoppingListScreen(viewModel, viewModel.pendingList)
            }
            composable(Screen.CheckedItems.route) {
                ShoppingListScreen(viewModel, viewModel.checkedList)
            }
        }
    }
}

@Composable
fun AppBottomNavigation(navController: NavController) {
    val navItems = listOf(Screen.AllItems, Screen.PendingItems, Screen.CheckedItems)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        navItems.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.label) },
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel, items: List<ShoppingItem>) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(viewModel.userName.value) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showNameDialog) {
        var errorMessage by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Set Your Nickname") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { 
                            tempName = it
                            errorMessage = ""
                        },
                        label = { Text("Nickname") },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setUserName(tempName) { success, message ->
                        if (success) {
                            showNameDialog = false
                        } else {
                            errorMessage = message
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    viewModel.duplicateWarningMessage.value?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDuplicateWarning() },
            title = { Text("Duplicate Item") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDuplicate() }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDuplicateWarning() }) {
                    Text("Cancel")
                }
            }
        )
    }

    viewModel.deleteWarningItem.value?.let { item ->
        var disableWarningFor5Min by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteWarning() },
            title = { Text("Confirm Delete") },
            text = {
                Column {
                    Text("Are you sure you want to delete '${item.itemName}'?")
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = disableWarningFor5Min,
                            onCheckedChange = { disableWarningFor5Min = it }
                        )
                        Text(
                            text = "Don't show again for 5 minutes",
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(item, disableWarningFor5Min) }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteWarning() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = viewModel.currentMode.value.ordinal) {
                Tab(
                    selected = viewModel.currentMode.value == ListMode.PERSONAL,
                    onClick = { viewModel.switchMode(ListMode.PERSONAL) },
                    text = { Text("Personal") }
                )
                Tab(
                    selected = viewModel.currentMode.value == ListMode.SHARED,
                    onClick = { viewModel.switchMode(ListMode.SHARED) },
                    text = { Text("Shared") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = viewModel.newItemText.value,
                        onValueChange = { viewModel.onNewItemTextChange(it) },
                        label = { Text("Enter New Item") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { viewModel.addItem() },
                        enabled = viewModel.newItemText.value.isNotBlank(),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Green,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Add")
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (viewModel.syncEnabled.value) "Disable Sync" else "Enable Sync") },
                                onClick = {
                                    viewModel.toggleSyncSetting()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set Nickname (${viewModel.userName.value})") },
                                onClick = {
                                    tempName = viewModel.userName.value
                                    showNameDialog = true
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export to Clipboard") },
                                onClick = {
                                    val exportText = items.joinToString("\n") { it.itemName }
                                    clipboardManager.setText(AnnotatedString(exportText))
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isLinked = viewModel.syncEnabled.value && 
                            if (viewModel.currentMode.value == ListMode.PERSONAL) {
                                viewModel.sharedShoppingList.any { it.itemName.equals(item.itemName, ignoreCase = true) && it.addedById == viewModel.userId }
                            } else {
                                viewModel.localShoppingList.any { it.itemName.equals(item.itemName, ignoreCase = true) }
                            }
                        
                        val externalCheck = viewModel.currentMode.value == ListMode.PERSONAL && 
                            item.haveItem && 
                            item.checkedById.isNotEmpty() && 
                            item.checkedById != viewModel.userId

                        ShoppingListItem(
                            item = item,
                            showOwner = viewModel.currentMode.value == ListMode.SHARED,
                            canDelete = viewModel.currentMode.value == ListMode.PERSONAL || 
                                        item.addedById == viewModel.userId || 
                                        item.addedById.isEmpty(),
                            isLinked = isLinked,
                            externalCheck = externalCheck,
                            onToggle = { viewModel.toggleItemChecked(item) },
                            onDelete = { viewModel.requestDelete(item) },
                            onCopy = { viewModel.copyToOtherList(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShoppingListItem(
    item: ShoppingItem,
    showOwner: Boolean,
    canDelete: Boolean,
    isLinked: Boolean,
    externalCheck: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.Center) {
                Checkbox(
                    checked = item.haveItem,
                    onCheckedChange = { onToggle() }
                )
                if (externalCheck) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Checked by someone else",
                        tint = Color.Magenta,
                        modifier = Modifier.size(16.dp).align(Alignment.TopEnd).padding(end = 4.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.itemName,
                        textDecoration = if (item.haveItem) TextDecoration.LineThrough else null,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isLinked) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Synced",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                        )
                    }
                }
                if (showOwner) {
                    Text(
                        text = "Added by: ${item.addedBy}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Light
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.SyncAlt, contentDescription = "Copy to other list", tint = Color.Blue)
            }
            if (canDelete) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("Delete")
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
    }
}
