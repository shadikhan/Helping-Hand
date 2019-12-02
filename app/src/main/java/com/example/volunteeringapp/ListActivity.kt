package com.example.volunteeringapp

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import android.widget.ExpandableListView
import android.widget.*
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.text.ParseException
import java.util.Date
import java.util.Locale
import kotlin.collections.ArrayList

class ListActivity : AppCompatActivity() {

    lateinit var expandableListView: ExpandableListView
    lateinit var createEventButton: Button
    lateinit var filterEventButton: Button
    internal lateinit var expandableListDetail: MutableList<Event>

    internal lateinit var databaseEvents: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        databaseEvents = FirebaseDatabase.getInstance().getReference("events")

        expandableListView = findViewById(R.id.expandableListView)
        createEventButton = findViewById(R.id.createEventButton)
        createEventButton.setOnClickListener {
            val addPostIntent = Intent(this, CreateEventActivity::class.java)
            startActivityForResult(addPostIntent, ADD_POST_REQUEST)
        }

        filterEventButton = findViewById(R.id.filterEventButton)
        filterEventButton.setOnClickListener {
            val filterIntent = Intent(this, FilterEventActivity::class.java)
            filterIntent.putExtra(CLEAR_PREFS_STR, CLEAR_PREFS)
            startActivityForResult(filterIntent, FILTER_REQUEST)
        }


        expandableListDetail = ArrayList()
    }

    override fun onStart() {
        super.onStart()
        databaseEvents.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (RESET_LISTVIEW) {
                    // Clearing the previous event list
                    expandableListDetail.clear()

                    // Iterating through all the event nodes
                    for (postSnapshot in dataSnapshot.children) {
                        // Getting event
                        val event = postSnapshot.getValue<Event>(Event::class.java) as Event

                        when (MENU_CURRENT) {
                            MENU_ALL_EVENTS -> {
                                // Adding any event to the list
                                expandableListDetail.add(event)
                                createEventButton.visibility = View.VISIBLE

                            }
                            MENU_CREATED_EVENTS -> {
                                // Adding created events unique to user
                                if (event.posterUid == intent.getStringExtra(LoginActivity.UserID))
                                    expandableListDetail.add(event)
                                createEventButton.visibility = View.VISIBLE
                            }
                            MENU_SIGNED_UP_EVENTS -> {
                                // Adding signed up events unique to user
                                if (event.registeredUsers.contains(
                                        intent?.getStringExtra(
                                            LoginActivity.UserID
                                        )
                                    )
                                )
                                    expandableListDetail.add(event)
                                createEventButton.visibility = View.INVISIBLE
                            }
                            MENU_SAVED_EVENTS -> {
                                // Adding saved events unique to user
                                if (event.savedUsers.contains(intent?.getStringExtra(LoginActivity.UserID)))
                                    expandableListDetail.add(event)
                                createEventButton.visibility = View.INVISIBLE
                            }
                        }
                    }

                    // Creating adapter using CustomExpandableList
                    val expandableListAdapter =
                        CustomExpandableListAdapter(this@ListActivity, expandableListDetail)
                    // Attaching adapter to expandableListView
                    expandableListView.setAdapter(expandableListAdapter)
                } else {
                    RESET_LISTVIEW = true
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {

            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ADD_POST_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data != null)
                addEvent(data)
        }
        if (requestCode == FILTER_REQUEST) {
            when(resultCode) {
                Activity.RESULT_OK -> {
                    if (data != null) {
                        filterEvents(data)
                        RESET_LISTVIEW = false
                    } else {
                        RESET_LISTVIEW = true
                    }
                }
                Activity.RESULT_CANCELED -> {
                    RESET_LISTVIEW = false
                }
            }
            CLEAR_PREFS = false
        }
    }

    private fun addEvent(data: Intent) {
        val title = data.getStringExtra(CreateEventActivity.TITLE)
        val description = data.getStringExtra(CreateEventActivity.DESCRIPTION)
        val capacityNum = data.getIntExtra(CreateEventActivity.CAPACITY, 0)
        val street = data.getStringExtra(CreateEventActivity.STREET)
        val city = data.getStringExtra(CreateEventActivity.CITY)
        val state = data.getStringExtra(CreateEventActivity.STATE)
        val postalCode = data.getStringExtra(CreateEventActivity.POSTALCODE)

        var startDateTime: Date
        var endDateTime: Date
        try {
            startDateTime = FORMAT.parse(data.getStringExtra(CreateEventActivity.START_DATETIME)!!)!!
            endDateTime = FORMAT.parse(data.getStringExtra(CreateEventActivity.END_DATETIME)!!)!!
        } catch (e: ParseException) {
            startDateTime = Date()
            endDateTime = Date()
        }

        /* Getting a unique id using push().getKey() method
        it will create a unique id and we will use it as the Primary Key for our event */
        val eventID = databaseEvents.push().key

        // Getting the user id of the user currently logged in
        val userID = intent.getStringExtra(LoginActivity.UserID)

        // Creating an Event Object
        val event = Event(userID!!, eventID!!, title!!, description!!, capacityNum,  street!!, city!!, state!!, postalCode!!, startDateTime, endDateTime, ArrayList(), ArrayList())

        // Saving the Event
        databaseEvents.child(eventID).setValue(event)

        // Displaying a success toast
        Toast.makeText(this, "Event added", Toast.LENGTH_LONG).show()
    }

    private fun filterEvents(data: Intent) {
        val fromDate: String = data.getStringExtra(FilterEventActivity.FROM_DATE)!!
        val toDate: String = data.getStringExtra(FilterEventActivity.TO_DATE)!!
        val fromTime: String = data.getStringExtra(FilterEventActivity.FROM_TIME)!!
        val toTime: String = data.getStringExtra(FilterEventActivity.TO_TIME)!!

        val dateFilterOn: Boolean = data.getBooleanExtra(FilterEventActivity.DATE_CHECK, false)
        val timeFilterOn: Boolean = data.getBooleanExtra(FilterEventActivity.TIME_CHECK, false)
        val capacityFilterOn: Boolean = data.getBooleanExtra(FilterEventActivity.CAPACITY_CHECK, false)

        var updatedExpandableListDetail: MutableList<Event> = expandableListDetail

        if (dateFilterOn) {
            val dateFilteredEvents: MutableList<Event> = ArrayList()

            for (event in updatedExpandableListDetail) {
                val sd: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(event.startDateTime).toString()
                val ed: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(event.endDateTime).toString()

                if (inDateRange(fromDate, toDate, sd, ed))
                    dateFilteredEvents.add(event)
            }

            updatedExpandableListDetail.clear()
            updatedExpandableListDetail = dateFilteredEvents
        }

        if (timeFilterOn) {
            val timeFilteredEvents: MutableList<Event> = ArrayList()

            for (event in updatedExpandableListDetail) {
                val st: String = SimpleDateFormat("h:mm a", Locale.US).format(event.startDateTime).toString()
                val et: String = SimpleDateFormat("h:mm a", Locale.US).format(event.endDateTime).toString()

                if (inTimeRange(fromTime, toTime, st, et))
                    timeFilteredEvents.add(event)
            }

            updatedExpandableListDetail.clear()
            updatedExpandableListDetail = timeFilteredEvents
        }

        if (capacityFilterOn) {
            val capacityFilteredEvents: MutableList<Event> = ArrayList()

            for (event in updatedExpandableListDetail) {
                if (event.registeredUsers.size < event.capacityNum)
                    capacityFilteredEvents.add(event)
            }

            updatedExpandableListDetail.clear()
            updatedExpandableListDetail = capacityFilteredEvents
        }

        expandableListDetail = updatedExpandableListDetail
        val expandableListAdapter = CustomExpandableListAdapter(this@ListActivity, expandableListDetail)
        expandableListView.setAdapter(expandableListAdapter)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menu.add(Menu.NONE, MENU_ALL_EVENTS, Menu.NONE, "All Events")
        menu.add(Menu.NONE, MENU_CREATED_EVENTS, Menu.NONE, "My Created Events")
        menu.add(Menu.NONE, MENU_SIGNED_UP_EVENTS, Menu.NONE, "My Signed Up Events")
        menu.add(Menu.NONE, MENU_SAVED_EVENTS, Menu.NONE, "My Saved Events")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_ALL_EVENTS -> {
                MENU_CURRENT = MENU_ALL_EVENTS
                CLEAR_PREFS = true
                onStart()
                return true
            }
            MENU_CREATED_EVENTS -> {
                MENU_CURRENT = MENU_CREATED_EVENTS
                CLEAR_PREFS = true
                onStart()
                return true
            }
            MENU_SIGNED_UP_EVENTS -> {
                MENU_CURRENT = MENU_SIGNED_UP_EVENTS
                CLEAR_PREFS = true
                onStart()
                return true
            }
            MENU_SAVED_EVENTS -> {
                MENU_CURRENT = MENU_SAVED_EVENTS
                CLEAR_PREFS = true
                onStart()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val ADD_POST_REQUEST = 0
        private val FILTER_REQUEST = 1

        // ID for menu items
        private val MENU_ALL_EVENTS = Menu.FIRST
        private val MENU_CREATED_EVENTS = Menu.FIRST + 1
        private val MENU_SIGNED_UP_EVENTS = Menu.FIRST + 2
        private val MENU_SAVED_EVENTS = Menu.FIRST + 3

        private var MENU_CURRENT = MENU_ALL_EVENTS

        private var RESET_LISTVIEW = true

        val CLEAR_PREFS_STR = "clear filter preferences"
        private var CLEAR_PREFS = false

        private fun inDateRange (fromDate: String, toDate: String, eventStartDate: String, eventEndDate: String): Boolean {
            // The from and to date by which to filter an event
            val fromDateTokens = fromDate.split("-")
            val toDateTokens = toDate.split("-")
            val fromYear = fromDateTokens[0].toInt()
            val toYear = toDateTokens[0].toInt()
            val fromMonth = fromDateTokens[1].toInt()
            val toMonth = toDateTokens[1].toInt()
            val fromDay = fromDateTokens[2].toInt()
            val toDay = toDateTokens[2].toInt()

            // The start and end date of an event
            val eventSDTokens = eventStartDate.split("-")
            val eventEDTokens = eventEndDate.split("-")
            val eventStartYear = eventSDTokens[0].toInt()
            val eventEndYear = eventEDTokens[0].toInt()
            val eventStartMonth = eventSDTokens[1].toInt()
            val eventEndMonth = eventEDTokens[1].toInt()
            val eventStartDay = eventSDTokens[2].toInt()
            val eventEndDay = eventEDTokens[2].toInt()

            if (eventStartYear < fromYear || eventStartYear > toYear || eventEndYear < fromYear || eventEndYear > toYear) {
                return false
            }

            if (eventStartYear == fromYear) {
                if (eventStartMonth < fromMonth || (eventStartMonth == fromMonth && eventStartDay < fromDay)) {
                    return false
                }
            }

            if (eventStartYear == toYear) {
                if (eventStartMonth > toMonth || (eventStartMonth == toMonth && (eventStartDay > toDay - 0))) {
                    return false
                }
            }

            if (eventEndYear == fromYear) {
                if (eventEndMonth < fromMonth || (eventEndMonth == fromMonth && (eventEndDay < fromDay + 0))) {
                    return false
                }
            }

            if (eventEndYear == toYear) {
                if (eventEndMonth > toMonth || (eventEndMonth == toMonth && eventEndDay > toDay)) {
                    return false
                }
            }

            return true
        }

        private fun inTimeRange (fromTime: String, toTime: String, eventStartTime: String, eventEndTime: String): Boolean {
            // The from and to time by which to filter an event
            val fromTimeTokens = fromTime.split(":")
            val toTimeTokens = toTime.split(":")
            val fromHour = addAM_PM(fromTimeTokens[0].toInt(), fromTimeTokens[1].substring(3))
            val toHour = addAM_PM(toTimeTokens[0].toInt(), toTimeTokens[1].substring(3))
            val fromMinute = fromTimeTokens[1].substring(0, 2).toInt()
            val toMinute = toTimeTokens[1].substring(0, 2).toInt()

            // The start and end time of an event
            val eventSTTokens = eventStartTime.split(":")
            val eventETTokens = eventEndTime.split(":")
            val eventStartHour = addAM_PM(eventSTTokens[0].toInt(), eventSTTokens[1].substring(3))
            val eventEndHour = addAM_PM(eventETTokens[0].toInt(), eventETTokens[1].substring(3))
            val eventStartMinute = eventSTTokens[1].substring(0, 2).toInt()
            val eventEndMinute = eventETTokens[1].substring(0, 2).toInt()

            if (eventStartHour < fromHour || eventStartHour > toHour || eventEndHour < fromHour || eventEndHour > toHour) {
                return false
            }

            if (eventStartHour == fromHour) {
                if (eventStartMinute < fromMinute) {
                    return false
                }
            }

            if (eventStartHour == toHour) {
                if (eventStartMinute >= toMinute - 0) {
                    return false
                }
            }

            if (eventEndHour == fromHour) {
                if (eventEndMinute <= fromMinute + 0) {
                    return false
                }
            }

            if (eventEndHour == toHour) {
                if (eventEndMinute > toMinute) {
                    return false
                }
            }

            return true
        }

        private fun addAM_PM(hour: Int, am_pm: String) : Int {
            when (am_pm) {
                "AM" -> {
                    if (hour == 12)
                        return 0
                    else
                        return hour
                }
                else -> {
                    if (hour == 12)
                        return hour
                    else
                        return hour + 12
                }
            }
        }

        val FORMAT = SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.US)
    }
}
