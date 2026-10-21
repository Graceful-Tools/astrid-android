package com.gracefultools.astrid.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One address-book entry as the server takes it: one per email address, as iOS sends them. */
data class DeviceContact(val email: String, val name: String?, val phoneNumber: String?)

/**
 * The address book, read for collaborator suggestions (AATD-68) — iOS's
 * `ContactsService.enumerateContacts`: every email address with its contact's name and first phone
 * number; a contact with no email is skipped. Reading only — what to do with them is the core's.
 */
object DeviceContacts {
    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun read(context: Context): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext emptyList()
        val resolver = context.contentResolver
        val phones = HashMap<Long, String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                cursor.getString(1)?.takeIf { it.isNotBlank() }?.let { phones.putIfAbsent(id, it) }
            }
        }
        val contacts = mutableListOf<DeviceContact>()
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val email = cursor.getString(1)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: continue
                val id = cursor.getLong(0)
                contacts += DeviceContact(email, cursor.getString(2)?.takeIf { it.isNotBlank() }, phones[id])
            }
        }
        contacts
    }
}
