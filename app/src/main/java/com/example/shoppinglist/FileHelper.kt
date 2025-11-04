package com.example.shoppinglist

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class FileHelper {
    val fileName = "listInfo.dat"

    fun writeData(list: MutableList<ShoppingItem>, context: Context) {
        try {
            val fos: FileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(list)
            oos.close()
            fos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readData(context: Context): MutableList<ShoppingItem> {
        var list: MutableList<ShoppingItem> = mutableStateListOf<ShoppingItem>()
        try {
            val fis: FileInputStream = context.openFileInput(fileName)
            val ois = ObjectInputStream(fis)
            list = ois.readObject() as MutableList<ShoppingItem>
        } catch(e: FileNotFoundException) {
            // This is fine, we'll just start with an empty list.
        } catch (e: Exception) {
            // For any other exceptions, we'll also start with an empty list
            // and log the error.
            e.printStackTrace()
        }
        return list
    }
}