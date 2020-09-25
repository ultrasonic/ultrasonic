package org.moire.ultrasonic.activity

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.moire.ultrasonic.R
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.util.Util

/**
 * Row Adapter to be used in the Server List
 * Converts a Server Setting into a displayable Row, and sets up the Row's context menu
 * @param manageMode: set to True if the default action by clicking the row is to edit the server
 * In Manage Mode the "Offline" setting is not visible, and the servers can be edited by
 * clicking the row.
 */
internal class ServerRowAdapter(
    private var context: Context,
    private var data: Array<ServerSetting>,
    private val model: ServerSettingsModel,
    private val activeServerProvider: ActiveServerProvider,
    private val manageMode: Boolean,
    private val serverDeletedCallback: ((Int) -> Unit),
    private val serverEditRequestedCallback: ((Int) -> Unit)
) : BaseAdapter() {

    companion object {
        private const val MENU_ID_EDIT = 1
        private const val MENU_ID_DELETE = 2
        private const val MENU_ID_UP = 3
        private const val MENU_ID_DOWN = 4
    }

    var inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    fun setData(data: Array<ServerSetting>) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return if (manageMode) data.size else data.size + 1
    }

    override fun getItem(position: Int): Any {
        return data[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    /**
     * Creates the Row representation of a Server Setting
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        var index = position
        // Skip "Offline" in manage mode
        if (manageMode) index++

        var vi: View? = convertView
        if (vi == null) vi = inflater.inflate(R.layout.server_row, parent, false)

        val text = vi?.findViewById<TextView>(R.id.server_name)
        val description = vi?.findViewById<TextView>(R.id.server_description)
        val layout = vi?.findViewById<RelativeLayout>(R.id.server_layout)
        val image = vi?.findViewById<ImageView>(R.id.server_image)
        val serverMenu = vi?.findViewById<ImageButton>(R.id.server_menu)

        if (index == 0) {
            text?.text = context.getString(R.string.main_offline)
            description?.text = ""
        } else {
            val setting = data.singleOrNull { t -> t.index == index }
            text?.text = setting?.name ?: ""
            description?.text = setting?.url ?: ""
            if (setting == null) serverMenu?.visibility = View.INVISIBLE
        }

        // Provide icons for the row
        if (index == 0) {
            serverMenu?.visibility = View.INVISIBLE
            image?.setImageDrawable(Util.getDrawableFromAttribute(context, R.attr.screen_on_off))
        } else {
            image?.setImageDrawable(Util.getDrawableFromAttribute(context, R.attr.server))
        }

        // Highlight the Active Server's row by changing its background
        if (index == activeServerProvider.getActiveServer().index) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                layout?.background = ContextCompat.getDrawable(context, R.drawable.select_ripple)
            } else {
                layout?.setBackgroundResource(
                    Util.getResourceFromAttribute(context, R.attr.list_selector_holo_selected)
                )
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                layout?.background = ContextCompat.getDrawable(context, R.drawable.default_ripple)
            } else {
                layout?.setBackgroundResource(
                    Util.getResourceFromAttribute(context, R.attr.list_selector_holo)
                )
            }
        }

        // Add the context menu for the row
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serverMenu?.background = ContextCompat.getDrawable(
                context,
                R.drawable.select_ripple_circle
            )
        } else {
            serverMenu?.setBackgroundColor(Color.TRANSPARENT)
        }

        serverMenu?.setOnClickListener { view -> serverMenuClick(view, index) }

        return vi
    }

    /**
     * Builds the Context Menu of a row when the "more" icon is clicked
     */
    private fun serverMenuClick(view: View, position: Int) {
        val menu = PopupMenu(context, view)
        val firstServer = 1
        var lastServer = count - 1

        if (!manageMode) {
            menu.menu.add(
                Menu.NONE,
                MENU_ID_EDIT,
                Menu.NONE,
                context.getString(R.string.server_menu_edit)
            )
        } else {
            lastServer++
        }

        menu.menu.add(
            Menu.NONE,
            MENU_ID_DELETE,
            Menu.NONE,
            context.getString(R.string.server_menu_delete)
        )

        if (position != firstServer) {
            menu.menu.add(
                Menu.NONE,
                MENU_ID_UP,
                Menu.NONE,
                context.getString(R.string.server_menu_move_up)
            )
        }

        if (position != lastServer) {
            menu.menu.add(
                Menu.NONE,
                MENU_ID_DOWN,
                Menu.NONE,
                context.getString(R.string.server_menu_move_down)
            )
        }

        menu.show()

        menu.setOnMenuItemClickListener { menuItem -> popupMenuItemClick(menuItem, position) }
    }

    /**
     * Handles the click on a context menu item
     */
    private fun popupMenuItemClick(menuItem: MenuItem, position: Int): Boolean {
        when (menuItem.itemId) {
            MENU_ID_EDIT -> {
                serverEditRequestedCallback.invoke(position)
                return true
            }
            MENU_ID_DELETE -> {
                serverDeletedCallback.invoke(position)
                return true
            }
            MENU_ID_UP -> {
                model.moveItemUp(position)
                return true
            }
            MENU_ID_DOWN -> {
                model.moveItemDown(position)
                return true
            }
            else -> return false
        }
    }
}
