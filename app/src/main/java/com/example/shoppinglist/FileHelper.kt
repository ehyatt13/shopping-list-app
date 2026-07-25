package com.example.shoppinglist

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass

class FileHelper {
    val fileName = "listInfo.dat"
    private val TAG = "ShoppingList_FileHelper"

    fun writeData(list: MutableList<ShoppingItem>, context: Context) {
        try {
            val fos: FileOutputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(list)
            oos.close()
            fos.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing data", e)
        }
    }

    fun readData(context: Context): MutableList<ShoppingItem> {
        var list: MutableList<ShoppingItem> = mutableStateListOf<ShoppingItem>()
        try {
            val fis: FileInputStream = context.openFileInput(fileName)
            val ois = LegacyRecoveryObjectInputStream(fis)
            list = ois.readObject() as MutableList<ShoppingItem>
            ois.close()
            fis.close()
            Log.d(TAG, "Successfully read ${list.size} items")
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "No data file found, starting fresh")
        } catch (e: Exception) {
            Log.e(TAG, "Critical error reading data", e)
        }
        return list
    }

    /**
     * A custom ObjectInputStream designed to recover data from obfuscated versions of the app.
     * It attempts to map unknown classes and mismatched serialVersionUIDs back to the
     * current ShoppingItem class.
     */
    private class LegacyRecoveryObjectInputStream(inputStream: java.io.InputStream) : ObjectInputStream(inputStream) {
        
        override fun resolveClass(desc: ObjectStreamClass): Class<*> {
            return try {
                super.resolveClass(desc)
            } catch (e: ClassNotFoundException) {
                // If the class is not found, it was likely obfuscated (e.g. "a.b.c").
                // Since ShoppingItem is our only custom serializable, we assume any
                // unknown class in the list stream should be ShoppingItem.
                Log.w("ShoppingList_Recovery", "Class not found: ${desc.name}. Mapping to ShoppingItem.")
                ShoppingItem::class.java
            }
        }

        override fun readClassDescriptor(): ObjectStreamClass {
            var result = super.readClassDescriptor()
            
            // If the class name looks like it might be an obfuscated ShoppingItem, 
            // or if the name matches but the UID is different, force it to match 
            // the current ShoppingItem descriptor.
            if (result.name.contains("ShoppingItem") || result.name.length < 5) {
                val localDescriptor = ObjectStreamClass.lookup(ShoppingItem::class.java)
                if (localDescriptor != null && result.serialVersionUID != localDescriptor.serialVersionUID) {
                    Log.w("ShoppingList_Recovery", 
                        "UID mismatch for ${result.name} (Stream: ${result.serialVersionUID}, Local: ${localDescriptor.serialVersionUID}). Forcing recovery.")
                    result = localDescriptor
                }
            }
            return result
        }
    }
}
