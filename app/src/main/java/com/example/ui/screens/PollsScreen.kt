package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PerformanceCategory
import com.example.model.Poll
import com.example.ui.components.HorizontalStageSearchBar
import com.example.ui.components.VotingPollCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PollsScreen(
    polls: List<Poll>,
    onVote: (pollId: String, candidateId: String) -> Unit,
    onPlayPerformance: (performanceId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryFilter by remember { mutableStateOf<PerformanceCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPolls = polls.filter { poll ->
        (selectedCategoryFilter == null || poll.category == selectedCategoryFilter) &&
            (searchQuery.isBlank() ||
                poll.title.contains(searchQuery, ignoreCase = true) ||
                poll.description.contains(searchQuery, ignoreCase = true) ||
                poll.candidates.any {
                    it.artistName.contains(searchQuery, ignoreCase = true) ||
                        it.title.contains(searchQuery, ignoreCase = true)
                })
    }

    val totalVotesCastByUser = polls.count { it.userVotedCandidateId != null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header: Arena Title & User Voting Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.linearGradient(listOf(ElectricCyan, NeonViolet)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToVote,
                        contentDescription = "Polls",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voting Arena",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Vote for the best in music & spoken word",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .background(StageSurfaceElevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$totalVotesCastByUser/${polls.size} Voted",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Search Bar
        HorizontalStageSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholderText = "Search battles by theme or performer...",
            testTag = "polls_search_bar",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Scrollable Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                    label = { Text("All Battles (${polls.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet,
                        selectedLabelColor = Color.White,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }

            item {
                FilterChip(
                    selected = selectedCategoryFilter == PerformanceCategory.MUSIC,
                    onClick = { selectedCategoryFilter = PerformanceCategory.MUSIC },
                    label = { Text("Music", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet,
                        selectedLabelColor = Color.White,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }

            item {
                FilterChip(
                    selected = selectedCategoryFilter == PerformanceCategory.SPOKEN_WORD,
                    onClick = { selectedCategoryFilter = PerformanceCategory.SPOKEN_WORD },
                    label = { Text("Spoken Word", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpokenWordPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Polls List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (filteredPolls.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = StageSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No battles match \"$searchQuery\"" else "No battles in this category",
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (searchQuery.isNotBlank() || selectedCategoryFilter != null) {
                                Button(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategoryFilter = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                                ) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredPolls) { poll ->
                    VotingPollCard(
                        poll = poll,
                        onVote = onVote,
                        onPlayPerformance = onPlayPerformance
                    )
                }
            }
        }
    }
}
