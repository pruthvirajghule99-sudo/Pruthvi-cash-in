package com.pruthvicash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val Navy = Color(0xFF102A43)
private val Green = Color(0xFF13A678)
private val Pale = Color(0xFFF3F7F6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PruthviCashApp() }
    }
}

class CashViewModel : ViewModel() {
    private val backend = SupabaseBackend()
    var email by mutableStateOf(""); private set
    var admin by mutableStateOf(false); private set
    var loans by mutableStateOf<List<Loan>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    fun login(email: String, password: String, register: Boolean) = viewModelScope.launch {
        busy = true; message = null
        try {
            if (register) {
                backend.register(email.trim(), password)
                message = "Account created. If email confirmation is enabled, verify your email, then sign in."
            } else {
                val user = backend.login(email.trim(), password)
                this@CashViewModel.email = user.first
                admin = user.second
                refreshLoans()
            }
        } catch (e: Exception) { message = e.message ?: "Unable to connect. Check your Supabase settings." }
        finally { busy = false }
    }

    fun refreshLoans() = viewModelScope.launch {
        try { loans = backend.fetchLoans() } catch (e: Exception) { message = e.message }
    }

    fun apply(amount: Int, months: Int, purpose: String) = viewModelScope.launch {
        busy = true
        try { backend.submitLoan(amount, months, purpose); message = "Your application was submitted."; loans = backend.fetchLoans() }
        catch (e: Exception) { message = e.message ?: "Could not submit application." }
        finally { busy = false }
    }

    fun review(id: String, status: String) = viewModelScope.launch {
        try { backend.updateStatus(id, status); loans = backend.fetchLoans(); message = "Application marked $status." }
        catch (e: Exception) { message = e.message ?: "Could not update application." }
    }

    fun dismissMessage() { message = null }
    fun signOut() { backend.signOut(); email = ""; admin = false; loans = emptyList() }
}

@Composable
fun PruthviCashApp(vm: CashViewModel = viewModel()) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Green, background = Pale)) {
        Surface(Modifier.fillMaxSize(), color = Pale) {
            if (vm.email.isBlank()) AuthScreen(vm) else Dashboard(vm)
        }
        vm.message?.let { text ->
            AlertDialog(onDismissRequest = vm::dismissMessage, confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } }, text = { Text(text) })
        }
    }
}

@Composable
private fun BrandHeader(subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 24.dp, vertical = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFF56D6A8), modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp)); Text("Pruthvi Cash", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp)); Text(subtitle, color = Color(0xFFD4E3EA), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuthScreen(vm: CashViewModel) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }; var adminMode by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader(if (registering) "Create an account to get started" else "Simple, transparent support for your goals")
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (registering) "Create account" else if (adminMode) "Administrator sign in" else "Welcome back", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Navy)
            OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(onClick = { vm.login(email, password, registering) }, enabled = !vm.busy && email.contains("@") && password.length >= 6, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (registering) "Create account" else "Sign in")
            }
            TextButton(onClick = { registering = !registering }) { Text(if (registering) "Already registered? Sign in" else "New to Pruthvi Cash? Create account") }
            TextButton(onClick = { adminMode = !adminMode }) { Text(if (adminMode) "Back to customer sign in" else "Administrator sign in") }
            Text("Your information is protected with secure account authentication.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun Dashboard(vm: CashViewModel) {
    var tab by remember { mutableStateOf(if (vm.admin) "Applications" else "Overview") }
    var showApply by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BrandHeader(if (vm.admin) "Administrator dashboard" else "Your money, your next step")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == "Profile") "My profile" else if (vm.admin) "Loan applications" else "Hello, ${vm.email.substringBefore('@')}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)
            IconButton(onClick = vm::signOut) { Icon(Icons.Default.ExitToApp, "Sign out", tint = Navy) }
        }
        if (tab == "Profile") ProfileContent(vm.email, vm.admin)
        else if (vm.admin) AdminContent(vm)
        else CustomerContent(vm, onApply = { showApply = true })
        NavigationBar(containerColor = Color.White) {
            val tabs = if (vm.admin) listOf("Applications", "Profile") else listOf("Overview", "Profile")
            tabs.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item; if (item != "Profile") vm.refreshLoans() }, icon = { Icon(if (item == "Profile") Icons.Default.AccountCircle else Icons.Default.CheckCircle, item) }, label = { Text(item) }) }
        }
    }
    if (showApply) ApplyDialog(onDismiss = { showApply = false }, onSubmit = { amount, months, purpose -> vm.apply(amount, months, purpose); showApply = false })
}

@Composable
private fun CustomerContent(vm: CashViewModel, onApply: () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Flexible loans for real life", color = Color(0xFFD4E3EA)); Spacer(Modifier.height(7.dp))
                Text("A clearer path forward.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp)); Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Apply for a loan") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your applications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
            IconButton(onClick = vm::refreshLoans) { Icon(Icons.Default.Refresh, "Refresh") }
        }
        if (vm.loans.isEmpty()) Text("No applications yet. Start when you’re ready.", modifier = Modifier.padding(vertical = 18.dp), color = Color.Gray)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(vm.loans, key = { it.id }) { LoanCard(it) } }
    }
}

@Composable
private fun AdminContent(vm: CashViewModel) {
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = vm::refreshLoans) { Icon(Icons.Default.Refresh, "Refresh") } }
        if (vm.loans.isEmpty()) Text("No loan applications found.", color = Color.Gray, modifier = Modifier.padding(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.loans, key = { it.id }) { loan ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("₹${loan.amount} · ${loan.months} months", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Navy)
                        Text("Applicant: ${loan.userId.take(12)}…", style = MaterialTheme.typography.bodySmall)
                        Text(loan.purpose, modifier = Modifier.padding(vertical = 6.dp))
                        Text("Status: ${loan.status.uppercase()}", color = Green)
                        if (loan.status == "pending") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.review(loan.id, "approved") }) { Text("Approve") }
                            TextButton(onClick = { vm.review(loan.id, "rejected") }) { Text("Decline") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanCard(loan: Loan) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("₹${loan.amount} · ${loan.months} months", fontWeight = FontWeight.Bold, color = Navy); Text(loan.purpose, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            Text(loan.status.replaceFirstChar { it.uppercase() }, color = if (loan.status == "approved") Green else Navy, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProfileContent(email: String, admin: Boolean) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Icon(Icons.Default.AccountCircle, null, tint = Green, modifier = Modifier.size(48.dp))
                Text("Account details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(14.dp)); Text("Email", color = Color.Gray); Text(email, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp)); Text("Account type", color = Color.Gray); Text(if (admin) "Administrator" else "Customer", fontWeight = FontWeight.Medium)
            }
        }
        Text("Manage your loan applications from the dashboard. For account assistance, contact Pruthvi Cash support.", color = Color.Gray)
    }
}

@Composable
private fun ApplyDialog(onDismiss: () -> Unit, onSubmit: (Int, Int, String) -> Unit) {
    var amount by remember { mutableFloatStateOf(25000f) }; var months by remember { mutableFloatStateOf(12f) }; var purpose by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Loan application") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Amount: ₹${amount.toInt()}", fontWeight = FontWeight.SemiBold)
            Slider(value = amount, onValueChange = { amount = it }, valueRange = 5000f..500000f, steps = 98)
            Text("Tenure: ${months.toInt()} months", fontWeight = FontWeight.SemiBold)
            Slider(value = months, onValueChange = { months = it }, valueRange = 3f..36f, steps = 10)
            OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, minLines = 2)
        }
    }, confirmButton = { Button(onClick = { if (purpose.isNotBlank()) onSubmit(amount.toInt(), months.toInt(), purpose.trim()) }, enabled = purpose.isNotBlank()) { Text("Submit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
