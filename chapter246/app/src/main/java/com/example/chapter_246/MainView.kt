package com.example.chapter_246

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView() {
    val viewModel: MainViewModel = viewModel()
    val scope: CoroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val controller: NavController = rememberNavController()
    val navBackStackEntry by controller.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val dialogOpen = remember {
        mutableStateOf(false)
    }
    val currentScreen = remember {
        viewModel.currentScreen.value
    }
    val title = remember {
        mutableStateOf(currentScreen.title)
    }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val isSheetFullScreen by remember { mutableStateOf(true) }
    val modifier = if (isSheetFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    val roundedCornerRadius = if (isSheetFullScreen) 0.dp else 12.dp
    val bottomBar: @Composable () -> Unit = {
        // TODO: [todos/kotlin-is-operator-and-smart-cast.md](../../../../../../../../todos/kotlin-is-operator-and-smart-cast.md)
        if (currentScreen is Screen.DrawerScreen || currentScreen == Screen.BottomBarScreen.Home) {
            NavigationBar(modifier = Modifier.wrapContentSize()) {
                screensInBottom.forEach { item ->
                    val isSelected = currentRoute == item.bRoute
                    // TODO: [todos/compose-icon-tint-vs-component-colors.md](../../../../../../../../todos/compose-icon-tint-vs-component-colors.md)
                    val tint = if (isSelected) Color.Red else Color.Black
                    NavigationBarItem(
                        selected = currentRoute == item.bRoute,
                        onClick = {
                            viewModel.currentScreen.value = item
                            title.value = item.bTitle
                            controller.navigate(item.bRoute)
                        },
                        icon = {
                            Icon(
                                painterResource(item.icon),
                                tint = tint,
                                contentDescription = item.bTitle
                            )
                        },
                        label = { Text(item.title) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = NavigationBarItemColors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.Black,
                            unselectedIconColor = Color.Black,
                            unselectedTextColor = Color.Black,
                            selectedIndicatorColor = Color.DarkGray,
                            disabledIconColor = Color.Black,
                            disabledTextColor = Color.Black,
                        )
                    )
                }
            }
        }
    }
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
            },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = roundedCornerRadius, topEnd = roundedCornerRadius)
        ) {
            MoreBottomSheet(modifier = Modifier)
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                LazyColumn(Modifier.padding(16.dp)) {
                    items(screensInDrawer) {
                        // DrawerItem(currentRoute == it.dRoute, it, {
                        //     scope.launch { drawerState.close() }
                        // })
                        NavigationDrawerItem(
                            label = { Text(it.dTitle) },
                            selected = currentRoute == it.dRoute,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (it.dRoute == "add_account") {
                                    dialogOpen.value = true
                                } else {
                                    controller.navigate(it.dRoute)
                                    title.value = it.dTitle
                                }
                            },
                            icon = { Icon(painterResource(it.icon), contentDescription = null) }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title.value) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            // TODO: [todos/compose-modal-bottom-sheet-state-and-sheetstate.md](../../../../../../../../todos/compose-modal-bottom-sheet-state-and-sheetstate.md)
                            showBottomSheet = true
                            scope.launch {
                                if (sheetState.isVisible) {
                                    sheetState.hide()
                                } else {
                                    sheetState.show()
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "메뉴 열기")
                        }
                    }
                )
            },
            bottomBar = bottomBar
        ) {
            Navigation(
                navController = controller,
                viewModel = viewModel,
                paddingValues = it
            )
            AccountDialog(
                dialogOpen = dialogOpen
            )
        }
    }
}

val screensInDrawer = listOf(
    Screen.DrawerScreen.Account,
    Screen.DrawerScreen.Subscription,
    Screen.DrawerScreen.AddAccount,
)

val screensInBottom = listOf(
    Screen.BottomBarScreen.Home,
    Screen.BottomBarScreen.Library,
    Screen.BottomBarScreen.Browse,
)

@Composable
fun DrawerItem(
    selected: Boolean,
    item: Screen.DrawerScreen,
    onDrawerItemClick: () -> Unit
) {
    val backgroundColor = if (selected) Color.DarkGray else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .background(backgroundColor)
            .clickable {
                onDrawerItemClick()
            }
    ) {
        Icon(
            painter = painterResource(id = item.icon),
            contentDescription = item.dTitle,
            Modifier.padding(end = 8.dp, top = 4.dp)
        )
        Text(
            text = item.dTitle,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}

@Composable
fun MoreBottomSheet(modifier: Modifier) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = modifier.padding(8.dp),
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Settings"
                )
                Text(text = "Settings", fontSize = 20.sp, color = Color.White)
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = modifier.padding(8.dp),
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Settings"
                )
                Text(text = "Settings", fontSize = 20.sp, color = Color.White)
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = modifier.padding(8.dp),
                    painter = painterResource(id = R.drawable.ic_settings),
                    contentDescription = "Settings"
                )
                Text(text = "Settings", fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun Navigation(
    navController: NavController,
    viewModel: MainViewModel,
    paddingValues: PaddingValues
) {
    NavHost(
        navController = navController as NavHostController,
        startDestination = Screen.DrawerScreen.Account.route,
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(Screen.BottomBarScreen.Home.route) {
            HomeView()
        }
        composable(Screen.BottomBarScreen.Library.route) {
            LibraryView()
        }
        composable(Screen.BottomBarScreen.Browse.route) {
            BrowseView()
        }
        composable(Screen.DrawerScreen.Account.route) {
            AccountView()
        }
        composable(Screen.DrawerScreen.Subscription.route) {
            Subscription()
        }
    }
}