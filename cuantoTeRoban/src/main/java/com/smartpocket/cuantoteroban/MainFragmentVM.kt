package com.smartpocket.cuantoteroban

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.smartpocket.cuantoteroban.chosencurrencies.ChosenCurrenciesRecyclerAdapter
import com.smartpocket.cuantoteroban.editortype.EditorType
import com.smartpocket.cuantoteroban.preferences.PreferencesManager
import com.smartpocket.cuantoteroban.repository.CurrencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainFragmentVM @Inject constructor() : ViewModel() {

    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + parentJob)
    private var refreshJob: Job? = null

    //    private val logger = Logger.getLogger(javaClass.simpleName)
    @Inject
    lateinit var repository: CurrencyRepository

    @Inject
    lateinit var preferences: PreferencesManager

    private var bankExchangeRate = 0.0
    private var invertBankExchangeRate = false
    private var bankExchangeRatePercentage = 0.0
    private var agencyExchangeRate = 0.0
    private var invertAgencyExchangeRate = false
    private var savingsPercentage = 0.0
    private var afipPercentage = 0.0
    private var payPalPercentage = 0.0
    private var discount = 0.0
    private var taxes = 0.0

    val amountLiveData = MutableLiveData(0.0)
    val discountLiveData = MutableLiveData(0.0)
    val taxesLiveData = MutableLiveData(0.0)
    val totalLiveData = MutableLiveData(0.0)
    val pesosLiveData = MutableLiveData(0.0)
    val creditCardLiveData = MutableLiveData(0.0)
    val blueLiveData = MutableLiveData(0.0)
    val exchangeAgencyLiveData = MutableLiveData(0.0)

    val currencyLiveData by lazy { MutableLiveData(preferences.currentCurrency) }
    val currencyEditorTypeLiveData = MutableLiveData<EditorType>()
    val isLoadingLiveData = MutableLiveData(false)
    val lastUpdateLiveData = MutableLiveData(Date(0))
    val errorLiveData = SingleLiveEvent<ErrorState>()

    enum class ErrorState { NO_INTERNET, DOWNLOAD_ERROR }

    init {
        onSettingsChanged()
    }

    private fun onAmountValueChanged(amount: Double) {
        val total = amountToTotal(amount)
        amountLiveData.postValue(amount)
        updateTotal(total)
        updatePesos(total)
        updateCreditCard(total)
        updateAgency(total)
        updateBlue(total)
    }

    private fun updateAmount(total: Double) {
        amountLiveData.postValue(totalToAmount(total))
    }

    private fun updateTotal(total: Double) {
        totalLiveData.postValue(total)
    }

    private fun updatePesos(total: Double) {
        pesosLiveData.postValue(totalToPesos(total))
    }

    private fun updateCreditCard(total: Double) {
        creditCardLiveData.postValue(totalToCreditCard(total))
    }

    private fun updateBlue(total: Double) {
        blueLiveData.postValue(totalToBlue(total))
    }

    private fun updateAgency(total: Double) {
        exchangeAgencyLiveData.postValue(totalToAgency(total))
    }

    fun onCalculatorValueChanged(editorType: EditorType, newValue: Double) {
        when (editorType) {
            EditorType.AMOUNT -> onAmountValueChanged(newValue)
            EditorType.PESOS -> onPesosValueChanged(newValue)
            EditorType.CREDIT_CARD -> onCreditCardValueChanged(newValue)
            EditorType.EXCHANGE_AGENCY -> onAgencyValueChanged(newValue)
            EditorType.PAYPAL -> {
            }
            EditorType.DISCOUNT -> onDiscountValueChanged(newValue)
            EditorType.TAXES -> onTaxesValueChanged(newValue)
            EditorType.TOTAL -> onTotalValueChanged(newValue)
            EditorType.SAVINGS -> {
            }
            EditorType.BLUE -> onBlueValueChanged(newValue)
        }
        if (editorType != EditorType.TAXES && editorType != EditorType.DISCOUNT) {
            preferences.lastConversionType = editorType
            preferences.lastConversionValue = newValue
        }
        currencyEditorTypeLiveData.postValue(editorType)
    }

    private fun onBlueValueChanged(newValue: Double) {
        blueLiveData.postValue(newValue)
        val total = blueToTotal(newValue)
        updateAmount(total)
        updateTotal(total)
        updatePesos(total)
        updateCreditCard(total)
        updateAgency(total)
    }

    private fun onTotalValueChanged(total: Double) {
        totalLiveData.postValue(total)
        updateAmount(total)
        updatePesos(total)
        updateCreditCard(total)
        updateAgency(total)
        updateBlue(total)
    }

    private fun onTaxesValueChanged(newValue: Double) {
        this.taxes = newValue
        preferences.taxes = newValue
        taxesLiveData.value = newValue
        onAmountValueChanged(amountLiveData.value ?: 0.0)
    }

    private fun onDiscountValueChanged(newValue: Double) {
        this.discount = newValue
        preferences.discount = newValue
        discountLiveData.value = newValue
        onAmountValueChanged(amountLiveData.value ?: 0.0)
    }

    private fun onAgencyValueChanged(newValue: Double) {
        exchangeAgencyLiveData.postValue(newValue)
        val total = agencyToTotal(newValue)
        updateAmount(total)
        updateTotal(total)
        updatePesos(total)
        updateCreditCard(total)
        updateBlue(total)
    }

    private fun onCreditCardValueChanged(newValue: Double) {
        creditCardLiveData.postValue(newValue)
        val total = creditCardToTotal(newValue)
        updateAmount(total)
        updateTotal(total)
        updatePesos(total)
        updateAgency(total)
        updateBlue(total)
    }

    private fun onPesosValueChanged(newValue: Double) {
        pesosLiveData.postValue(newValue)
        val total = pesosToTotal(newValue)
        updateAmount(total)
        updateTotal(total)
        updateCreditCard(total)
        updateAgency(total)
        updateBlue(total)
    }

    private fun totalToAmount(total: Double): Double {
        val totalWithoutTaxes = total / (1 + taxes / 100)
        //double totalWithoutTaxesOrDiscounts = totalWithoutTaxes / (1 - discount / 100); //THIS IS SOMETIMES  DIVIDING BY 0
        var totalWithoutTaxesOrDiscounts = 0.0
        if (discount != 100.0) totalWithoutTaxesOrDiscounts = totalWithoutTaxes / (1 - discount / 100)
        return totalWithoutTaxesOrDiscounts
    }

    private fun amountToTotal(amount: Double): Double {
        val amountWithDiscount = amount - discount * amount / 100
        return amountWithDiscount + taxes * amountWithDiscount / 100
    }

    private fun totalToPesos(total: Double): Double {
        var pesos = total * bankExchangeRate
        pesos += bankExchangeRatePercentage * pesos / 100 // suma porcentaje de correccion de la cotizacion
        return pesos
    }

    private fun pesosToTotal(pesos: Double): Double {
        if (bankExchangeRate == 0.0) return 0.0
        var total = pesos - bankExchangeRatePercentage * pesos / 100 // resta la correccion
        total /= bankExchangeRate
        return total
    }

    private fun totalToCreditCard(total: Double): Double {
        val pesos = totalToPesos(total)
        return pesos + afipPercentage * pesos / 100
    }

    private fun creditCardToTotal(creditCard: Double): Double {
        val pesos = creditCard / (1 + afipPercentage / 100)
        return pesosToTotal(pesos)
    }

    private fun totalToBlue(total: Double): Double {
        val blueRate = preferences.blueDollarToARSRate
        return total * blueRate
    }

    private fun blueToTotal(blue: Double): Double {
        val blueRate = preferences.blueDollarToARSRate
        return blue / blueRate
    }

    private fun totalToAgency(total: Double): Double {
        return total * agencyExchangeRate
    }

    private fun agencyToTotal(agency: Double): Double {
        return if (agencyExchangeRate == 0.0) 0.0 else agency / agencyExchangeRate
    }

    fun onSettingsChanged() {
        coroutineScope.launch {
            restoreLastConversion()
            refreshRates(false)
        }
    }

    fun onStart() {
        refreshRates(false)
    }

    private fun restoreLastConversion() {
        bankExchangeRate = if (preferences.isUseInternetBankExchangeRateEnabled) {
            preferences.internetExchangeRate
        } else {
            preferences.bankExchangeRate
        }
        bankExchangeRatePercentage = preferences.bankCorrectionPercentage
        agencyExchangeRate = preferences.agencyExchangeRate
        afipPercentage = preferences.afipPercentage
        payPalPercentage = preferences.payPalPercentage
        invertBankExchangeRate = preferences.isBankExchangeRateInverted
        invertAgencyExchangeRate = preferences.isAgencyExchangeRateInverted
        discount = preferences.discount
        taxes = preferences.taxes
        savingsPercentage = preferences.savingsPercentage

        if (invertBankExchangeRate) {
            if (bankExchangeRate != 0.0) bankExchangeRate = 1 / bankExchangeRate
        }

        if (invertAgencyExchangeRate) {
            if (agencyExchangeRate != 0.0) agencyExchangeRate = 1 / agencyExchangeRate
        }

        val currentCurrency = preferences.currentCurrency
        currencyLiveData.postValue(currentCurrency)
        lastUpdateLiveData.postValue(preferences.getLastUpdateDate(currentCurrency))
        discountLiveData.value = discount
        taxesLiveData.value = taxes

        val lastConversionType = preferences.lastConversionType
        if (lastConversionType != null) {
            onCalculatorValueChanged(lastConversionType, preferences.lastConversionValue)
        }
    }

    fun refreshRates(isForced: Boolean) {
        refreshJob?.cancel("Cancelling refresh to start another")
        refreshJob = coroutineScope.launch {
            isLoadingLiveData.postValue(true)
            val currency = preferences.currentCurrency
            try {
                repository.getCurrencyExchange(currency, CurrencyManager.ARS, 0.0, isForced)
                restoreLastConversion()
            } catch (e: Exception) {
                e.printStackTrace()
                errorLiveData.value =
                        if (isInternetAvailable()) ErrorState.DOWNLOAD_ERROR
                        else ErrorState.NO_INTERNET
            }
            isLoadingLiveData.postValue(false)
        }
    }

    fun onDeleteDiscount() {
        onDiscountValueChanged(0.0)
    }

    fun onDeleteTaxes() {
        onTaxesValueChanged(0.0)
    }

    override fun onCleared() {
        super.onCleared()
        coroutineScope.cancel("ViewModel onCleared()")
    }

    @Suppress("DEPRECATION")
    private fun isInternetAvailable(): Boolean {
        var result = false
        val connectivityManager =
                MyApplication.applicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                    connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            result = when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result = when (type) {
                        ConnectivityManager.TYPE_WIFI -> true
                        ConnectivityManager.TYPE_MOBILE -> true
                        ConnectivityManager.TYPE_ETHERNET -> true
                        else -> false
                    }
                }
            }
        }
        return result
    }

}