package com.ionasalgados.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ionasalgados.app.data.local.dao.*
import com.ionasalgados.app.data.local.entity.*

@Database(
    entities = [
        ProdutoEntity::class,
        CategoriaEntity::class,
        PedidoEntity::class,
        ItemPedidoEntity::class,
        ClienteEntity::class,
        ConfiguracaoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IonaDatabase : RoomDatabase() {
    abstract fun produtoDao(): ProdutoDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun pedidoDao(): PedidoDao
    abstract fun itemPedidoDao(): ItemPedidoDao
    abstract fun clienteDao(): ClienteDao
    abstract fun configuracaoDao(): ConfiguracaoDao
}
