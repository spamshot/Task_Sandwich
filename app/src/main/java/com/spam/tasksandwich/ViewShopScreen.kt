package com.spam.tasksandwich

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewShopScreen(
    onNavigateBack: () -> Unit,
    viewModel: ViewShopViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // FIX 2: LaunchedEffect already runs in a coroutine — no need for scope.launch inside.
    LaunchedEffect(uiState.checkoutSuccess) {
        if (uiState.checkoutSuccess) {
            snackbarHostState.showSnackbar("Purchase successful!")
            viewModel.onCheckoutHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onCheckoutHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            uiState.shopItems.isEmpty() -> {
                EmptyShopState(modifier = Modifier.padding(paddingValues))
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                ) {
                    // FIX 1: Points card had padding only on the end, not start.
                    // Text appeared clipped on left side. Now padding is symmetric.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Card(modifier = Modifier.padding(4.dp)) {
                            Text(
                                text = "Points: ${uiState.userPointsInRoom}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), // ✅ symmetric
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp), // ✅ Adaptive instead of Fixed(2)
                        // so tablets show 3-4 columns naturally
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.shopItems) { item ->
                            val isSelected = uiState.cartItems.any { it.id == item.id }
                            ShopItemCard(
                                item = item,
                                isSelected = isSelected,
                                onClick = { viewModel.toggleCartItem(item) }
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.checkout() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        enabled = uiState.cartItems.isNotEmpty() && uiState.userPointsInRoom >= uiState.cartTotal
                    ) {
                        Text("Checkout (${uiState.cartTotal} pts)")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyShopState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No shop items have been created yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ShopItemCard(item: ShopItem, isSelected: Boolean, onClick: () -> Unit) {
    val cardColors = if (isSelected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${item.cost} pts",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}