package com.example.expenseapp.data.repository.mapper

import com.example.expenseapp.data.local.entity.*
import com.example.expenseapp.domain.model.*

fun ExpenseEntity.toDomain(splits: List<Split> = emptyList()) = Expense(
    id = id,
    groupId = group_id,
    paidById = paid_by_id,
    description = description,
    amount = amount,
    currency = "USD", 
    date = date,
    categoryId = "default",
    splits = splits
)

fun Expense.toEntity() = ExpenseEntity(
    id = id,
    group_id = groupId,
    paid_by_id = paidById,
    description = description,
    amount = amount,
    currency = currency,
    date = date,
    category_id = categoryId
)

fun UserEntity.toDomain() = User(
    id = id,
    name = name,
    email = email,
    avatarUrl = avatar_url
)

fun User.toEntity() = UserEntity(
    id = id,
    name = name,
    email = email,
    avatar_url = avatarUrl
)

fun GroupEntity.toDomain() = Group(
    id = id,
    name = name,
    createdAt = created_at
)

fun Group.toEntity() = GroupEntity(
    id = id,
    name = name,
    created_at = createdAt
)

fun SplitEntity.toDomain() = Split(
    id = id,
    expenseId = expense_id,
    owedById = owed_by_id,
    amountOwed = amount_owed
)

fun Split.toEntity() = SplitEntity(
    id = id,
    expense_id = expenseId,
    owed_by_id = owedById,
    amount_owed = amountOwed
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    iconName = icon_name,
    colorHex = color_hex
)

fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    icon_name = iconName,
    color_hex = colorHex
)
