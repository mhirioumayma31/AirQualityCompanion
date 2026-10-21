
package com.example.airqualitycompanion
import androidx.compose.material.icons.filled.FavoriteBorder
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.airqualitycompanion.data.AirQuality
import com.example.airqualitycompanion.data.City
import com.example.airqualitycompanion.data.Pollutant
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AirQualityApp()
            }
        }
    }
}

private data class AppTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun AirQualityApp(
    vm: AirQualityViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current

    val tabs = listOf(
        AppTab("home", "Accueil", Icons.Default.Home),
        AppTab("forecast", "Prévisions", Icons.Default.Timeline),
        AppTab("favorites", "Favoris", Icons.Default.Favorite),
        AppTab("compare", "Comparer", Icons.Default.CompareArrows),
        AppTab("details", "Polluants", Icons.Default.Info)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun loadLocation() {
        val manager = context.getSystemService(
            android.content.Context.LOCATION_SERVICE
        ) as LocationManager

        try {
            val providers = manager.getProviders(true)
            val location = providers.mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }.maxByOrNull { it.time }

            if (location == null) {
                vm.showError(
                    "Position indisponible. Active la localisation GPS, " +
                            "puis réessaie après quelques instants."
                )
            } else {
                vm.loadCity(
                    City(
                        name = "Ma position",
                        country = "Position actuelle",
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                )
                navController.navigate("home") {
                    launchSingleTop = true
                }
            }
        } catch (_: SecurityException) {
            vm.showError("Autorisation de localisation refusée.")
        } catch (e: Exception) {
            vm.showError("Impossible d'obtenir la position : ${e.message}")
        }
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                loadLocation()
            } else {
                vm.showError(
                    "Autorise la localisation pour utiliser cette fonction."
                )
            }
        }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(
                                    navController.graph.findStartDestination().id
                                ) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    state = state,
                    onSearch = vm::searchCity,
                    onRefresh = vm::refresh,
                    onLocation = {
                        val fine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        val coarse = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (fine || coarse) {
                            loadLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onFavorite = { city -> vm.toggleFavorite(city) },
                    isFavorite = { city -> vm.isFavorite(city) },
                    onNavigate = { route ->
                        navController.navigate(route)
                    }
                )
            }

            composable("forecast") {
                ForecastScreen(state.airQuality)
            }

            composable("favorites") {
                FavoritesScreen(
                    cities = state.favorites,
                    onOpen = { city ->
                        vm.loadCity(city)
                        navController.navigate("home")
                    },
                    onRemove = vm::toggleFavorite
                )
            }

            composable("compare") {
                CompareScreen(
                    state = state,
                    onCompare = vm::compareCity,
                    onClear = vm::clearComparison
                )
            }

            composable("details") {
                DetailsScreen(state.airQuality)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: AirQualityUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocation: () -> Unit,
    onFavorite: (City) -> Unit,
    isFavorite: (City) -> Boolean,
    onNavigate: (String) -> Unit
) {
    var query by remember { mutableStateOf("Castres") }

    LaunchedEffect(state.airQuality?.city) {
        state.airQuality?.city?.let { city ->
            query = if (city.name == "Ma position") "" else city.name
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Air Quality Companion",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("Comprends la qualité de l'air autour de toi.")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher une ville") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onSearch(query) }) {
                Icon(Icons.Default.Search, contentDescription = "Rechercher")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onLocation,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Ma position")
            }

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Actualiser")
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Chargement des données…")
        }

        state.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(error)
                    TextButton(onClick = onRefresh) {
                        Text("Réessayer")
                    }
                }
            }
        }

        state.airQuality?.let { air ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        air.city.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(air.city.country)
                }

                IconButton(onClick = { onFavorite(air.city) }) {
                    Icon(
                        imageVector = if (isFavorite(air.city)) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = if (isFavorite(air.city)) {
                            "Retirer des favoris"
                        } else {
                            "Ajouter aux favoris"
                        },
                        tint = if (isFavorite(air.city)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            AqiCard(air)

            Text(
                "Principaux polluants",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            air.pollutants.forEach { pollutant ->
                PollutantCard(pollutant)
            }

            Text(
                "Dernière donnée : ${air.time}",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(
                onClick = { onNavigate("details") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Comprendre les polluants")
            }

            OutlinedButton(
                onClick = { onNavigate("forecast") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Voir les prévisions horaires")
            }

            Text(
                "Source : Open-Meteo. Les valeurs sont des estimations " +
                        "modélisées et ne remplacent pas les mesures officielles " +
                        "ni les conseils d'un professionnel de santé.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AqiCard(air: AirQuality) {
    val aqi = air.aqi
    val label = when {
        aqi == null -> "Indice indisponible"
        aqi <= 20 -> "Bon"
        aqi <= 40 -> "Assez bon"
        aqi <= 60 -> "Modéré"
        aqi <= 80 -> "Mauvais"
        aqi <= 100 -> "Très mauvais"
        else -> "Extrêmement mauvais"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Indice européen de qualité de l'air")
            Text(
                aqi?.toString() ?: "—",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.titleLarge)
            Text(
                "Plus l'indice augmente, plus la pollution est importante.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PollutantCard(pollutant: Pollutant) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pollutant.name,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(pollutant.symbol)
                }
                Text(
                    pollutant.value?.let {
                        String.format(Locale.FRANCE, "%.1f %s", it, pollutant.unit)
                    } ?: "—",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                pollutant.description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Composable
private fun DetailsScreen(air: AirQuality?) {
    var selectedSymbol by remember { mutableStateOf("PM2.5") }

    val pollutant = air?.pollutants?.find {
        it.symbol == selectedSymbol
    }

    val values = when (selectedSymbol) {
        "PM2.5" -> air?.hourlyPm25.orEmpty()
        "PM10" -> air?.hourlyPm10.orEmpty()
        "O₃" -> air?.hourlyOzone.orEmpty()
        "NO₂" -> air?.hourlyNo2.orEmpty()
        else -> emptyList()
    }

    val explanation = when (selectedSymbol) {
        "PM2.5" -> Triple(
            "Que sont les PM2.5 ?",
            "Ce sont des particules de diamètre inférieur ou égal à 2,5 µm. Leur petite taille leur permet de pénétrer profondément dans les poumons.",
            "Combustion, trafic routier, chauffage et réactions atmosphériques."
        )

        "PM10" -> Triple(
            "Que sont les PM10 ?",
            "Ce sont des particules en suspension de diamètre inférieur ou égal à 10 µm. Elles comprennent notamment des poussières et des particules issues de la combustion.",
            "Poussières, chantiers, trafic routier et activités industrielles."
        )

        "O₃" -> Triple(
            "Qu'est-ce que l'ozone ?",
            "L'ozone au niveau du sol est un polluant secondaire : il se forme dans l'atmosphère par des réactions chimiques sous l'effet du soleil.",
            "Réactions entre différents polluants précurseurs, favorisées par l'ensoleillement."
        )

        else -> Triple(
            "Qu'est-ce que le NO₂ ?",
            "Le dioxyde d'azote est un gaz irritant qui peut affecter les voies respiratoires.",
            "Trafic routier, moteurs, chauffage et autres combustions."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Explorer les polluants",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            air?.let { "Ville : ${it.city.name}" }
                ?: "Recherche une ville depuis l'accueil pour consulter les données."
        )

        if (air == null) return@Column

        Text(
            "Choisis un polluant",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("PM2.5", "PM10", "O₃", "NO₂").forEach { symbol ->
                FilterChip(
                    selected = selectedSymbol == symbol,
                    onClick = { selectedSymbol = symbol },
                    label = { Text(symbol) }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    pollutant?.name ?: selectedSymbol,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    pollutant?.value?.let {
                        String.format(
                            Locale.FRANCE,
                            "%.1f %s",
                            it,
                            pollutant.unit
                        )
                    } ?: "Concentration indisponible",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text("Mesure actuelle estimée")

                Text(
                    pollutant?.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Évolution horaire",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Concentration en µg/m³ · prochaines heures",
                    style = MaterialTheme.typography.bodySmall
                )

                if (values.none { it != null }) {
                    Text(
                        "Les prévisions horaires ne sont pas disponibles pour ce polluant."
                    )
                } else {
                    PollutantTrendChart(values.take(24))

                    val validValues = values.filterNotNull().take(24)

                    Text(
                        "Minimum : ${
                            String.format(
                                Locale.FRANCE,
                                "%.1f",
                                validValues.minOrNull()
                            )
                        } µg/m³"
                    )

                    Text(
                        "Maximum : ${
                            String.format(
                                Locale.FRANCE,
                                "%.1f",
                                validValues.maxOrNull()
                            )
                        } µg/m³"
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    explanation.first,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(explanation.second)

                HorizontalDivider()

                Text(
                    "Sources possibles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(explanation.third)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Comment interpréter la mesure ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Une concentration isolée ne suffit pas à déterminer le risque sanitaire. " +
                            "L'interprétation dépend du polluant, de la durée d'exposition " +
                            "et du référentiel utilisé."
                )

                Text(
                    "Les données d'Open-Meteo sont issues de modèles et ne remplacent " +
                            "pas les mesures officielles locales."
                )
            }
        }
    }
}

@Composable
private fun PollutantTrendChart(values: List<Double?>) {
    val validValues = values.filterNotNull()

    if (validValues.size < 2) {
        Text("Pas assez de mesures pour tracer une évolution.")
        return
    }

    val maxValue = (validValues.maxOrNull() ?: 1.0)
        .coerceAtLeast(1.0)

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val horizontalPadding = 8.dp.toPx()
        val verticalPadding = 12.dp.toPx()
        val chartWidth = size.width - horizontalPadding * 2
        val chartHeight = size.height - verticalPadding * 2

        // Lignes horizontales de repère
        for (i in 0..4) {
            val y = verticalPadding + chartHeight * i / 4f

            drawLine(
                color = gridColor,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width - horizontalPadding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Les heures conservent leur position, même si une valeur manque.
        val stepX = chartWidth / (values.size - 1).coerceAtLeast(1)

        val points = values.mapIndexed { index, value ->
            value?.let {
                Offset(
                    x = horizontalPadding + index * stepX,
                    y = verticalPadding + chartHeight -
                            (it / maxValue).toFloat() * chartHeight
                )
            }
        }

        for (i in 0 until points.lastIndex) {
            val start = points[i]
            val end = points[i + 1]

            if (start != null && end != null) {
                drawLine(
                    color = lineColor,
                    start = start,
                    end = end,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        points.filterNotNull().forEach { point ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = point
            )
        }
    }
}

@Composable
private fun ForecastScreen(air: AirQuality?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Prévisions PM2.5",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (air == null || air.hourlyPm25.isEmpty()) {
            Text("Aucune prévision disponible. Charge d'abord une ville.")
            return@Column
        }

        Text(
            "Ville : ${air.city.name} · Concentration en µg/m³"
        )

        Pm25Chart(air.hourlyPm25.filterNotNull().take(24))

        Text(
            "Prévisions horaires",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        air.hourlyTimes.take(24).forEachIndexed { index, time ->
            val value = air.hourlyPm25.getOrNull(index)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(time.replace("T", " "))

                Text(
                    value?.let {
                        String.format(Locale.FRANCE, "%.1f µg/m³", it)
                    } ?: "Indisponible",
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()
        }
    }
}

@Composable
private fun Pm25Chart(values: List<Double>) {
    if (values.size < 2) {
        Text("Pas assez de valeurs pour tracer le graphique.")
        return
    }

    val maxValue = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(
                x = index * stepX,
                y = size.height -
                        (value.toFloat() / maxValue.toFloat()) * size.height
            )
        }

        for (index in 0 until points.lastIndex) {
            drawLine(
                color = androidx.compose.ui.graphics.Color(0xFF168A78),
                start = points[index],
                end = points[index + 1],
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        points.forEach { point ->
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFF168A78),
                radius = 4f,
                center = point
            )
        }
    }
}

@Composable
private fun FavoritesScreen(
    cities: List<City>,
    onOpen: (City) -> Unit,
    onRemove: (City) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Mes villes favorites",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (cities.isEmpty()) {
            Text(
                "Aucun favori pour le moment. " +
                        "Recherche une ville puis appuie sur le cœur."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = cities,
                    key = { "${it.latitude}_${it.longitude}" }
                ) { city ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onOpen(city) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(city.name, fontWeight = FontWeight.Bold)
                                Text(city.country)
                                Text(
                                    "Appuie pour consulter la qualité de l'air",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            IconButton(onClick = { onRemove(city) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Supprimer le favori"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareScreen(
    state: AirQualityUiState,
    onCompare: (String) -> Unit,
    onClear: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Comparer deux villes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            "La ville de référence est celle affichée sur l'accueil. " +
                    "Saisis une deuxième ville pour comparer les indices."
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Deuxième ville") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onCompare(query) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CompareArrows, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Comparer")
        }

        state.comparisonError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        val first = state.airQuality
        val second = state.comparison

        if (first == null) {
            Text("Charge d'abord une ville depuis l'accueil.")
        } else if (second != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ComparisonRow(
                        cityName = first.city.name,
                        aqi = first.aqi
                    )
                    HorizontalDivider()
                    ComparisonRow(
                        cityName = second.city.name,
                        aqi = second.aqi
                    )
                    HorizontalDivider()

                    val difference = if (
                        first.aqi != null && second.aqi != null
                    ) {
                        kotlin.math.abs(first.aqi - second.aqi)
                    } else null

                    Text(
                        difference?.let {
                            "Écart entre les indices : $it"
                        } ?: "Impossible de calculer l'écart."
                    )

                    Text(
                        "Un indice plus faible correspond généralement " +
                                "à une meilleure qualité de l'air."
                    )

                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Effacer la comparaison")
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(cityName: String, aqi: Int?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(cityName, fontWeight = FontWeight.Bold)
            Text("Indice européen")
        }
        Text(
            aqi?.toString() ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}