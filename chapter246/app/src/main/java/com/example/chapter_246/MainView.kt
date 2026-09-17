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
    // FIXME: `remember { viewModel.currentScreen.value }` 는 상태를 "구독"하지 않고
    //  최초 값 하나만 복사해 온다. 이후 `currentScreen` 이 바뀌어도 이 변수는 영원히 그대로다.
    //  그래서 하단 바 표시 조건(`currentScreen is Screen.DrawerScreen ...`)이 화면 전환에 반응하지 않는다.
    //  고치기: 상태를 그대로 읽는다. → `val currentScreen by viewModel.currentScreen`
    val currentScreen = remember {
        viewModel.currentScreen.value
    }
    // FIXME: 제목을 별도 상태로 또 들고 있어 `currentScreen` 과 이중으로 관리된다.
    //  두 곳을 각각 갱신해야 해서(아래 onClick 들이 그렇게 하고 있다) 한쪽만 바꾸면 화면이 어긋난다.
    //  고치기: 제목은 파생 값으로 계산한다. → `val title = currentScreen.title`
    val title = remember {
        mutableStateOf(currentScreen.title)
    }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    // FIXME: 값이 바뀌는 곳이 없는데 상태로 감쌌다. "이건 바뀔 수 있다"는 잘못된 신호를 준다.
    //  고치기: `val isSheetFullScreen = true`
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
                            // FIXME: 화면이 ViewModel 의 상태를 직접 대입하고 있다(단방향 데이터 흐름 위반).
                            //  ViewModel 에 이미 `setCurrentScreen()` 이 있는데 쓰지 않는다.
                            //  고치기: `viewModel.setCurrentScreen(item)`
                            viewModel.currentScreen.value = item
                            title.value = item.bTitle
                            // FIXME: 하단 바 이동에 NavOptions 가 없어 탭을 옮길 때마다 백스택이 무한히 쌓인다.
                            //  뒤로 가기를 누른 횟수만큼 이전 탭들을 거슬러 올라가게 된다.
                            //  고치기:
                            //      controller.navigate(item.bRoute) {
                            //          popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                            //          launchSingleTop = true
                            //          restoreState = true
                            //      }
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
                        // FIXME: `NavigationBarItemColors` 생성자를 직접 호출하고 있다.
                        //  모든 색을 빠짐없이 적어야 하고, 라이브러리가 색 항목을 추가하면 컴파일이 깨진다.
                        //  고치기: `NavigationBarItemDefaults.colors(selectedIconColor = ..., ...)` 로
                        //         바꾸고 싶은 항목만 이름 인자로 지정한다.
                        // FIXME: `Color.White/Black/DarkGray` 하드코딩이라 다크 모드에서 대비가 깨진다.
                        //  고치기: `MaterialTheme.colorScheme.onSecondaryContainer` 등 색 역할을 쓴다.
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

// FIXME: `modifier` 가 기본값 없는 필수 파라미터이고, 내부에서 최상위가 아니라
//  자식 요소들에 중복 적용되고 있다(Column, Icon 3개).
//  Compose API 가이드라인은 `modifier: Modifier = Modifier` 를 첫 선택 파라미터로 두고
//  최상위 요소에 한 번만 적용하라고 안내한다.
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
        // FIXME: `NavController` 로 받아 놓고 `NavHostController` 로 내려 캐스팅하고 있다.
        //  타입이 맞지 않으면 런타임에 `ClassCastException` 이 난다.
        //  고치기: 처음부터 필요한 타입으로 받는다. → `navController: NavHostController`
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