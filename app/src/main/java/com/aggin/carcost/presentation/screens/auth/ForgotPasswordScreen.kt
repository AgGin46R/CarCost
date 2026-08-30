package com.aggin.carcost.presentation.screens.auth

import androidx.compose.ui.res.stringResource
import com.aggin.carcost.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

/**
 * Восстановление пароля: почта → код из письма → новый пароль.
 *
 * Один экран на все три шага: это одна задача, и разбивать её на отдельные
 * экраны с переходами значит заставлять человека держать в голове, где он
 * находится.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auth_vosstanovlenie_parolya)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Внутри процесса «назад» возвращает на предыдущий шаг,
                        // а не выкидывает из восстановления целиком
                        if (uiState.step == RecoveryStep.EMAIL) navController.popBackStack()
                        else viewModel.back()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (uiState.step == RecoveryStep.EMAIL) Icons.Default.LockReset
                              else Icons.Default.MarkEmailRead,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))

            when (uiState.step) {
                RecoveryStep.EMAIL -> {
                    StepHint(stringResource(R.string.auth_prishlem_kod_na_pochtu_vvedite_adres_na))
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::updateEmail,
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionButton(stringResource(R.string.auth_otpravit_kod), uiState.isLoading, viewModel::sendCode)
                }

                RecoveryStep.CODE -> {
                    StepHint(
                        stringResource(R.string.auth_kod_otpravlen_na_esli_takoy_akkaunt, uiState.email) +
                            stringResource(R.string.auth_proverte_papku_spam_pisma_ot_servisov)
                    )
                    OutlinedTextField(
                        value = uiState.code,
                        onValueChange = viewModel::updateCode,
                        label = { Text(stringResource(R.string.auth_kod_iz_pisma)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionButton(stringResource(R.string.auth_prodolzhit), uiState.isLoading, viewModel::verifyCode)
                    TextButton(
                        onClick = viewModel::sendCode,
                        enabled = !uiState.isLoading
                    ) { Text(stringResource(R.string.auth_otpravit_kod_esche_raz)) }
                }

                RecoveryStep.NEW_PASSWORD -> {
                    StepHint(stringResource(R.string.auth_kod_podoshel_pridumayte_novyy_parol))
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::updatePassword,
                        label = { Text(stringResource(R.string.auth_novyy_parol)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.passwordRepeat,
                        onValueChange = viewModel::updatePasswordRepeat,
                        label = { Text(stringResource(R.string.auth_esche_raz)) },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionButton(stringResource(R.string.auth_sohranit_parol), uiState.isLoading, viewModel::savePassword)
                }

                // Молча закрыть экран нельзя: человек не поймёт, сменился пароль
                // или что-то сорвалось, и полезет восстанавливать заново
                RecoveryStep.DONE -> {
                    StepHint(stringResource(R.string.auth_parol_izmenen_voydite_s_novym_parolem))
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.auth_pereyti_ko_vhodu), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StepHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ActionButton(label: String, isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
