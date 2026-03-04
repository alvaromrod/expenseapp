package com.example.expenseapp.data.local

import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val appSchema = Schema(
    Table(
        name = "expenses",
        columns = listOf(
            Column.text("group_id"),
            Column.text("paid_by_id"),
            Column.text("description"),
            Column.real("amount"),
            Column.text("currency"),
            Column.integer("date"),
            Column.text("category_id")
        )
    ),
    Table(
        name = "users",
        columns = listOf(
            Column.text("name"),
            Column.text("email"),
            Column.text("avatar_url")
        )
    ),
    Table(
        name = "groups",
        columns = listOf(
            Column.text("name"),
            Column.integer("created_at")
        )
    ),
    Table(
        name = "splits",
        columns = listOf(
            Column.text("expense_id"),
            Column.text("owed_by_id"),
            Column.real("amount_owed")
        )
    ),
    Table(
        name = "categories",
        columns = listOf(
            Column.text("name"),
            Column.text("icon_name"),
            Column.text("color_hex")
        )
    ),
    Table(
        name = "group_members",
        columns = listOf(
            Column.text("group_id"),
            Column.text("user_id")
        )
    )
)
