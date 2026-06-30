package com.ionasalgados.app.data.local.dao

import androidx.room.*
import com.ionasalgados.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao {
    @Query("SELECT * FROM produtos WHERE ativo = 1 ORDER BY ordem, nome")
    fun getAll(): Flow<List<ProdutoEntity>>

    @Query("SELECT * FROM produtos WHERE id = :id")
    suspend fun getById(id: Long): ProdutoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(produtos: List<ProdutoEntity>)

    @Query("DELETE FROM produtos")
    suspend fun deleteAll()
}

@Dao
interface CategoriaDao {
    @Query("SELECT * FROM categorias WHERE ativo = 1 ORDER BY ordem, nome")
    fun getAll(): Flow<List<CategoriaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categorias: List<CategoriaEntity>)

    @Query("DELETE FROM categorias")
    suspend fun deleteAll()
}

@Dao
interface PedidoDao {
    @Query("SELECT * FROM pedidos ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PedidoEntity>>

    @Query("SELECT * FROM pedidos WHERE status NOT IN ('finalizado', 'cancelado') ORDER BY createdAt DESC")
    fun getAtivos(): Flow<List<PedidoEntity>>

    @Query("SELECT * FROM pedidos WHERE id = :id")
    suspend fun getById(id: Long): PedidoEntity?

    @Query("SELECT * FROM pedidos WHERE date(createdAt) = date('now', 'localtime') ORDER BY createdAt DESC")
    fun getHoje(): Flow<List<PedidoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pedido: PedidoEntity)

    @Update
    suspend fun update(pedido: PedidoEntity)
}

@Dao
interface ItemPedidoDao {
    @Query("SELECT * FROM itens_pedido WHERE pedidoId = :pedidoId")
    suspend fun getByPedidoId(pedidoId: Long): List<ItemPedidoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(itens: List<ItemPedidoEntity>)
}

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes ORDER BY nome")
    fun getAll(): Flow<List<ClienteEntity>>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun getById(id: Long): ClienteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clientes: List<ClienteEntity>)

    @Query("DELETE FROM clientes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clientes")
    suspend fun deleteAll()
}

@Dao
interface ConfiguracaoDao {
    @Query("SELECT * FROM configuracoes")
    suspend fun getAll(): List<ConfiguracaoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ConfiguracaoEntity)
}
