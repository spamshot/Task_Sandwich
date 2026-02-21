package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewShopScreen(
    onNavigateBack: () -> Unit,
    viewModel: ViewShopViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSuccessOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.checkoutSuccess) {
        if (uiState.checkoutSuccess) {
            showSuccessOverlay = true
            viewModel.onCheckoutHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { viewModel.onCheckoutHandled() }
    }

    // Auto-dismiss success overlay after 2.5s
    LaunchedEffect(showSuccessOverlay) {
        if (showSuccessOverlay) {
            delay(2500)
            showSuccessOverlay = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.roomName.isNotBlank()) "${uiState.roomName} · Shop"
                            else "Shop",
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A1A2E),
                        scrolledContainerColor = Color(0xFF1A1A2E)
                    )
                )
            }
        ) { paddingValues ->
            when {
                uiState.isLoading -> {
                    Box(
                        Modifier.fillMaxSize().padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                uiState.shopItems.isEmpty() -> {
                    ShopEmptyState(modifier = Modifier.padding(paddingValues))
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // REDESIGN 2: Balance banner
                        ShopBalanceBanner(
                            balance = uiState.userPointsInRoom,
                            cartTotal = uiState.cartTotal,
                            hasCartItems = uiState.cartItems.isNotEmpty()
                        )

                        // REDESIGN 3: Grid with enhanced cards
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.shopItems, key = { it.id }) { item ->
                                val isSelected = uiState.cartItems.any { it.id == item.id }
                                val remaining = uiState.userPointsInRoom - uiState.cartTotal
                                // Dim if user can't afford it (and it's not already selected)
                                val canAfford = isSelected || remaining >= item.cost
                                ShopItemCard(
                                    item = item,
                                    isSelected = isSelected,
                                    canAfford = canAfford,
                                    onClick = { viewModel.toggleCartItem(item) }
                                )
                            }
                        }

                        // REDESIGN 4 + 5: Cart summary + checkout
                        ShopCartBar(
                            cartItems = uiState.cartItems,
                            cartTotal = uiState.cartTotal,
                            canCheckout = uiState.cartItems.isNotEmpty()
                                    && uiState.userPointsInRoom >= uiState.cartTotal,
                            onCheckout = { viewModel.checkout() }
                        )
                    }
                }
            }
        }

        // REDESIGN 6: Full-screen success overlay
        AnimatedVisibility(
            visible = showSuccessOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF2F0FDF4)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🎉", style = MaterialTheme.typography.displayLarge)
                    Text(
                        "Purchase Sent!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF16A34A)
                    )
                    Text(
                        "Waiting for admin approval",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================
// ShopBalanceBanner — live balance with after-purchase preview
// ============================================================
@Composable
fun ShopBalanceBanner(
    balance: Int,
    cartTotal: Int,
    hasCartItems: Boolean
) {
    val afterBalance = balance - cartTotal
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "YOUR BALANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$balance pts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Live after-purchase preview
                AnimatedVisibility(visible = hasCartItems) {
                    Text(
                        text = "After purchase: $afterBalance pts",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (afterBalance >= 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                    )
                }
            }
            Text("🏆", style = MaterialTheme.typography.displaySmall)
        }
    }
}

// ============================================================
// ShopItemCard — icon area, badges, checkmark, dim if can't afford
// ============================================================
@Composable
fun ShopItemCard(
    item: ShopItem,
    isSelected: Boolean,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isSelected) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) Color(0xFF3B6BDC) else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .alpha(if (canAfford) 1f else 0.4f)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(enabled = canAfford || isSelected) { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Checkmark overlay top-right
            if (isSelected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp),
                    shape = CircleShape,
                    color = Color(0xFF3B6BDC)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon area
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFFDBEAFE)
                    else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎁", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${item.cost} pts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) Color(0xFF1D4ED8)
                    else MaterialTheme.colorScheme.primary
                )

                // Badges
                if (item.autoRedeem || item.mysteryText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.autoRedeem) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Text(
                                    "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (item.mysteryText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFFAF5FF),
                                border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                            ) {
                                Text(
                                    "Mystery",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7C3AED),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// ShopCartBar — cart summary + checkout button
// ============================================================
@Composable
fun ShopCartBar(
    cartItems: List<ShopItem>,
    cartTotal: Int,
    canCheckout: Boolean,
    onCheckout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Cart summary — only when items selected
            AnimatedVisibility(visible = cartItems.isNotEmpty()) {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    Text(
                        text = "CART",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    cartItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "•",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${item.cost} pts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Total",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$cartTotal pts",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Checkout button
            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth(),
                enabled = canCheckout,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A1A2E),
                    disabledContainerColor = Color(0xFF1A1A2E).copy(alpha = 0.38f)
                )
            ) {
                Text(
                    text = if (cartItems.isEmpty()) "Checkout"
                    else "Checkout · $cartTotal pts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

// ============================================================
// ShopEmptyState — upgraded with emoji + copy
// ============================================================
@Composable
fun ShopEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🛍", style = MaterialTheme.typography.displayMedium)
            Text(
                "Nothing in the shop yet.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ask your admin to add some rewards!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}