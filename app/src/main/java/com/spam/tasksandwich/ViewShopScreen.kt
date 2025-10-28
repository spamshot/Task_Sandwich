package com.spam.tasksandwich

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    val scope = rememberCoroutineScope()

    // This effect shows a snackbar after a successful checkout.
    LaunchedEffect(uiState.checkoutSuccess) {
        if (uiState.checkoutSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar("Purchase successful!")
            }
            viewModel.onCheckoutHandled() // Reset the event
        }
    }

    // This effect shows an error message (e.g., "Not enough points!").
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
            }
            viewModel.onCheckoutHandled() // Use the same handler to clear the error
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("${uiState.roomName} Shop") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go Back")
                    }
                },
                actions = {
                    // Display the user's current point total for this room in the top bar.
                    Text(
                        text = "Your Points: ${uiState.userPointsInRoom}",
                        modifier = Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.shopItems.isEmpty()) {
            EmptyShopState(modifier = Modifier.padding(paddingValues))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // The Grid of shop items, which takes up the available vertical space.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.shopItems) { item ->
                        // Determine if the current item is in the cart.
                        val isSelected = uiState.cartItems.any { it.id == item.id }
                        ShopItemCard(
                            item = item,
                            isSelected = isSelected,
                            onClick = { viewModel.toggleCartItem(item) }
                        )
                    }
                }

                // The Checkout button, displayed at the bottom of the screen.
                Button(
                    onClick = { viewModel.checkout() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    // The button is only enabled if the cart has items and the user has enough points.
                    enabled = uiState.cartItems.isNotEmpty() && uiState.userPointsInRoom >= uiState.cartTotal
                ) {
                    Text("Checkout (${uiState.cartTotal} pts)")
                }
            }
        }
    }
}

/**
 * A composable for the "No Shop Items" message.
 */
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

/**
 * The card for a single shop item. It changes color based on selection
 * and calls the ViewModel when clicked.
 */
@Composable
fun ShopItemCard(item: ShopItem, isSelected: Boolean, onClick: () -> Unit) {
    // Conditionally set the card's background color.
    val cardColors = if (isSelected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f) // Make the card square
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
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
        }
    }
}