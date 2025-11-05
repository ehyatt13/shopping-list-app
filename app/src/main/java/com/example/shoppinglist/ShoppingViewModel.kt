package com.example.shoppinglist

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.Serializable

data class ShoppingItem(val itemName: String, val haveItem: Boolean = false) : Serializable

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val fileHelper = FileHelper()
    var newItemText = mutableStateOf("")
        private set

    var shoppingList = mutableStateListOf<ShoppingItem>()
        private set

    val pendingList: List<ShoppingItem>
        get() = shoppingList.filter { !it.haveItem }

    val checkedList: List<ShoppingItem>
        get() = shoppingList.filter { it.haveItem }

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    init {
        shoppingList.addAll(fileHelper.readData(getApplication()))
    }

    fun onNewItemTextChange(newText: String) {
        newItemText.value = newText
    }

    fun addItem() {
        val newItemName = newItemText.value.trim()
        if (newItemName.isNotBlank() && shoppingList.none { it.itemName.equals(newItemName, ignoreCase = true) }) {
            shoppingList.add(ShoppingItem(itemName = newItemName))
            newItemText.value = ""
            saveData()
        } else {
            viewModelScope.launch {
                _snackbarMessage.emit("The shopping list already contains an item with the name '$newItemName'.")
            }
        }
    }

    fun removeItem(item: ShoppingItem) {
        shoppingList.remove(item)
        saveData()
    }

    fun toggleItemChecked(item: ShoppingItem) {
        val index = shoppingList.indexOf(item)
        if (index != -1) {
            shoppingList[index] = item.copy(haveItem = !item.haveItem)
            saveData()
        }
    }

    private fun saveData() {
        fileHelper.writeData(shoppingList.toMutableList(), getApplication())
    }
}