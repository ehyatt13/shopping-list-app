package com.example.shoppinglist

import android.app.Application
import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.UUID

@Keep
data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val itemName: String = "",
    val haveItem: Boolean = false,
    val addedBy: String = "Me",
    val addedById: String = "",
    val checkedById: String = ""
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

enum class ListMode {
    PERSONAL, SHARED
}

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val fileHelper = FileHelper()
    private val database = FirebaseDatabase.getInstance().reference.child("shopping_list")
    private val nicknamesRef = FirebaseDatabase.getInstance().reference.child("nicknames")
    private val auth = FirebaseAuth.getInstance()
    private val sharedPrefs = application.getSharedPreferences("shopping_prefs", Context.MODE_PRIVATE)

    var newItemText = mutableStateOf("")
        private set

    var currentMode = mutableStateOf(ListMode.PERSONAL)
        private set

    val userId: String = sharedPrefs.getString("user_id", null) ?: UUID.randomUUID().toString().also {
        sharedPrefs.edit().putString("user_id", it).apply()
    }

    var userName = mutableStateOf(sharedPrefs.getString("user_name", "User_${userId.take(4)}") ?: "")
        private set

    var syncEnabled = mutableStateOf(sharedPrefs.getBoolean("sync_enabled", true))
        private set

    var isReverseMode = mutableStateOf(sharedPrefs.getBoolean("reverse_mode", false))
        private set

    var localShoppingList = mutableStateListOf<ShoppingItem>()
        private set

    var sharedShoppingList = mutableStateListOf<ShoppingItem>()
        private set

    val shoppingList: List<ShoppingItem>
        get() = if (currentMode.value == ListMode.PERSONAL) localShoppingList else sharedShoppingList

    val pendingList: List<ShoppingItem>
        get() = shoppingList.filter { !it.haveItem }

    val checkedList: List<ShoppingItem>
        get() = shoppingList.filter { it.haveItem }

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    var duplicateWarningMessage = mutableStateOf<String?>(null)
        private set

    var deleteWarningItem = mutableStateOf<ShoppingItem?>(null)
        private set

    private var deleteWarningDisabledUntil = 0L

    private var onDuplicateConfirm: (() -> Unit)? = null

    init {
        val loadedItems = fileHelper.readData(getApplication()).map { item ->
            // Java Serialization can bypass Kotlin's nullability rules at runtime.
            // We ensure ALL fields are non-null and repaired if corrupted by older versions.
            @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
            if (item.id == null || item.itemName == null || item.addedBy == null || item.addedById == null || item.checkedById == null) {
                ShoppingItem(
                    id = item.id ?: UUID.randomUUID().toString(),
                    itemName = item.itemName ?: "Unknown Item",
                    haveItem = item.haveItem,
                    addedBy = item.addedBy ?: userName.value,
                    addedById = item.addedById ?: userId,
                    checkedById = item.checkedById ?: ""
                )
            } else {
                item
            }
        }
        localShoppingList.addAll(loadedItems)
        signInAnonymously()
    }

    private fun signInAnonymously() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            setupFirebaseListener()
        } else {
            auth.signInAnonymously().addOnSuccessListener {
                setupFirebaseListener()
            }.addOnFailureListener {
                viewModelScope.launch {
                    _snackbarMessage.emit("Failed to sign in anonymously: ${it.message}")
                }
            }
        }
    }

    fun confirmDuplicate() {
        onDuplicateConfirm?.invoke()
        onDuplicateConfirm = null
        duplicateWarningMessage.value = null
    }

    fun dismissDuplicateWarning() {
        duplicateWarningMessage.value = null
        onDuplicateConfirm = null
    }

    fun requestDelete(item: ShoppingItem) {
        val currentTime = System.currentTimeMillis()
        if (currentTime < deleteWarningDisabledUntil) {
            removeItem(item)
        } else {
            deleteWarningItem.value = item
        }
    }

    fun confirmDelete(item: ShoppingItem, disableWarningFor5Min: Boolean) {
        if (disableWarningFor5Min) {
            deleteWarningDisabledUntil = System.currentTimeMillis() + 5 * 60 * 1000
        }
        removeItem(item)
        deleteWarningItem.value = null
    }

    fun dismissDeleteWarning() {
        deleteWarningItem.value = null
    }

    fun removeItem(item: ShoppingItem) {
        if (currentMode.value == ListMode.PERSONAL) {
            localShoppingList.remove(item)
            saveLocalData()
        } else {
            // Allow deletion if you are the owner OR if the item is orphaned (addedById is empty)
            if (item.addedById == userId || item.addedById.isEmpty()) {
                database.child(item.id).removeValue()
            } else {
                viewModelScope.launch {
                    _snackbarMessage.emit("Only the owner can delete this item from the shared list.")
                }
            }
        }
    }

    private fun setupFirebaseListener() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val items = mutableListOf<ShoppingItem>()
                snapshot.children.forEach { itemSnapshot ->
                    itemSnapshot.getValue(ShoppingItem::class.java)?.let { items.add(it) }
                }
                sharedShoppingList.clear()
                sharedShoppingList.addAll(items)

                // If sync is enabled, update local items that match shared items
                if (syncEnabled.value) {
                    items.forEach { sharedItem ->
                        if (sharedItem.addedById == userId) {
                            val localIndex = localShoppingList.indexOfFirst { it.itemName.equals(sharedItem.itemName, ignoreCase = true) }
                            if (localIndex != -1) {
                                val localItem = localShoppingList[localIndex]
                                if (localItem.haveItem != sharedItem.haveItem) {
                                    localShoppingList[localIndex] = localItem.copy(
                                        haveItem = sharedItem.haveItem,
                                        checkedById = sharedItem.checkedById
                                    )
                                    saveLocalData()
                                }
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                viewModelScope.launch {
                    _snackbarMessage.emit("Failed to sync shared list: ${error.message}")
                }
            }
        })
    }

    fun setUserName(name: String, onResult: (Boolean, String) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onResult(false, "Nickname cannot be empty")
            return
        }

        nicknamesRef.child(trimmedName).get().addOnSuccessListener { snapshot ->
            val existingUserId = snapshot.getValue(String::class.java)
            if (existingUserId != null && existingUserId != userId) {
                onResult(false, "Nickname '$trimmedName' is already taken")
            } else {
                // Remove old nickname mapping if it exists
                val oldName = userName.value
                if (oldName != trimmedName) {
                    nicknamesRef.child(oldName).removeValue()
                }

                // Map new nickname
                nicknamesRef.child(trimmedName).setValue(userId).addOnSuccessListener {
                    userName.value = trimmedName
                    sharedPrefs.edit().putString("user_name", trimmedName).apply()
                    updateMyItemsNickname(trimmedName)
                    onResult(true, "Nickname updated")
                }
            }
        }.addOnFailureListener {
            onResult(false, "Failed to check nickname: ${it.message}")
        }
    }

    private fun updateMyItemsNickname(newName: String) {
        // Find all items in shared list that I added and update them
        sharedShoppingList.forEach { item ->
            if (item.addedById == userId && item.addedBy != newName) {
                database.child(item.id).child("addedBy").setValue(newName)
            }
        }
    }

    fun switchMode(mode: ListMode) {
        currentMode.value = mode
    }

    fun toggleSyncSetting() {
        val newValue = !syncEnabled.value
        syncEnabled.value = newValue
        sharedPrefs.edit().putBoolean("sync_enabled", newValue).apply()
    }

    fun toggleReverseMode() {
        val newValue = !isReverseMode.value
        isReverseMode.value = newValue
        sharedPrefs.edit().putBoolean("reverse_mode", newValue).apply()
    }

    fun onNewItemTextChange(newText: String) {
        newItemText.value = newText
    }

    fun addItem() {
        val newItemName = newItemText.value.trim()
        if (newItemName.isBlank()) {
            viewModelScope.launch { _snackbarMessage.emit("Item name cannot be empty") }
            return
        }

        val isDuplicate = shoppingList.any { it.itemName.equals(newItemName, ignoreCase = true) }

        if (isDuplicate) {
            if (currentMode.value == ListMode.SHARED) {
                val myDuplicate = sharedShoppingList.any {
                    it.itemName.equals(newItemName, ignoreCase = true) && it.addedById == userId
                }
                if (myDuplicate) {
                    viewModelScope.launch {
                        _snackbarMessage.emit("You already have '$newItemName' on the shared list.")
                    }
                } else {
                    duplicateWarningMessage.value = "Someone else has '$newItemName' on the shared list. Add your own instance?"
                    onDuplicateConfirm = { executeAddItem(newItemName) }
                }
            } else {
                viewModelScope.launch {
                    _snackbarMessage.emit("The personal list already contains '$newItemName'.")
                }
            }
        } else {
            executeAddItem(newItemName)
        }
    }

    private fun executeAddItem(name: String) {
        val newItem = ShoppingItem(
            itemName = name,
            addedBy = userName.value,
            addedById = userId
        )
        if (currentMode.value == ListMode.PERSONAL) {
            localShoppingList.add(newItem)
            saveLocalData()
        } else {
            database.child(newItem.id).setValue(newItem)
        }
        newItemText.value = ""
    }


    fun toggleItemChecked(item: ShoppingItem) {
        val updatedItem = item.copy(
            haveItem = !item.haveItem,
            checkedById = userId
        )
        if (currentMode.value == ListMode.PERSONAL) {
            val index = localShoppingList.indexOfFirst { it.id == item.id }
            if (index != -1) {
                localShoppingList[index] = updatedItem
                saveLocalData()

                // Sync to shared if enabled
                if (syncEnabled.value) {
                    sharedShoppingList.find { it.itemName.equals(item.itemName, ignoreCase = true) && it.addedById == userId }?.let { sharedMatch ->
                        database.child(sharedMatch.id).setValue(sharedMatch.copy(
                            haveItem = updatedItem.haveItem,
                            checkedById = userId
                        ))
                    }
                }
            }
        } else {
            database.child(item.id).setValue(updatedItem)
            
            // Sync to local if enabled
            if (syncEnabled.value) {
                val localIndex = localShoppingList.indexOfFirst { it.itemName.equals(item.itemName, ignoreCase = true) }
                if (localIndex != -1) {
                    localShoppingList[localIndex] = localShoppingList[localIndex].copy(
                        haveItem = updatedItem.haveItem,
                        checkedById = userId
                    )
                    saveLocalData()
                }
            }
        }
    }

    fun copyToOtherList(item: ShoppingItem) {
        val targetList = if (currentMode.value == ListMode.PERSONAL) sharedShoppingList else localShoppingList
        val targetMode = if (currentMode.value == ListMode.PERSONAL) ListMode.SHARED else ListMode.PERSONAL

        val alreadyExists = targetList.any { it.itemName.equals(item.itemName, ignoreCase = true) }

        if (alreadyExists) {
            if (targetMode == ListMode.SHARED) {
                val myDuplicate = sharedShoppingList.any {
                    it.itemName.equals(item.itemName, ignoreCase = true) && it.addedById == userId
                }
                if (myDuplicate) {
                    viewModelScope.launch {
                        _snackbarMessage.emit("You already have '${item.itemName}' on the shared list.")
                    }
                } else {
                    duplicateWarningMessage.value = "Someone else has '${item.itemName}' on the shared list. Copy your own instance?"
                    onDuplicateConfirm = { executeCopyToOtherList(item, targetMode) }
                }
            } else {
                viewModelScope.launch {
                    _snackbarMessage.emit("The personal list already contains '${item.itemName}'.")
                }
            }
        } else {
            executeCopyToOtherList(item, targetMode)
        }
    }

    private fun executeCopyToOtherList(item: ShoppingItem, targetMode: ListMode) {
        val newItem = item.copy(
            id = UUID.randomUUID().toString(),
            addedBy = userName.value,
            addedById = userId
        )

        if (targetMode == ListMode.SHARED) {
            database.child(newItem.id).setValue(newItem)
            viewModelScope.launch { _snackbarMessage.emit("Copied to Shared list") }
        } else {
            localShoppingList.add(newItem)
            saveLocalData()
            viewModelScope.launch { _snackbarMessage.emit("Copied to Personal list") }
        }
    }

    private fun saveLocalData() {
        fileHelper.writeData(localShoppingList.toMutableList(), getApplication())
    }
}
