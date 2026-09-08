package liquibase.ext.starrocks.lockservice

import liquibase.Scope
import liquibase.exception.DatabaseException
import liquibase.exception.LockException
import liquibase.executor.Executor
import liquibase.executor.ExecutorService
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.lockservice.StandardLockService
import liquibase.statement.core.LockDatabaseChangeLogStatement
import liquibase.statement.core.SelectFromDatabaseChangeLogLockStatement
import liquibase.statement.core.UnlockDatabaseChangeLogStatement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

class StarRocksLockServiceTest {
    private lateinit var database: StarRocksDatabase
    private lateinit var executor: Executor
    private lateinit var service: StarRocksLockService

    @BeforeEach
    fun setUp() {
        database = mock(StarRocksDatabase::class.java)
        `when`(database.databaseChangeLogLockTableName).thenReturn("DATABASECHANGELOGLOCK")
        `when`(database.escapeTableName(null, null, "DATABASECHANGELOGLOCK_MUTEX"))
            .thenReturn("DATABASECHANGELOGLOCK_MUTEX")
        executor = mock(Executor::class.java)
        service = spy(object : StarRocksLockService() {
            override fun initializeForRecovery() { init() }
        })
        service.setDatabase(database)
        Scope.getCurrentScope().getSingleton(ExecutorService::class.java)
            .setExecutor("jdbc", database, executor)
        // Table creation is covered by integration; keep unit tests at the executor boundary.
        doNothing().`when`(service).init()
        StandardLockService::class.java.getDeclaredField("hasDatabaseChangeLogLockTable").apply {
            isAccessible = true
            set(service, true)
        }
        `when`(executor.queryForObject(any(SelectFromDatabaseChangeLogLockStatement::class.java),
            eq(Boolean::class.javaObjectType))).thenReturn(false)
        `when`(executor.update(any(LockDatabaseChangeLogStatement::class.java))).thenReturn(1)
        `when`(executor.update(any(UnlockDatabaseChangeLogStatement::class.java))).thenReturn(1)
    }

    @AfterEach
    fun tearDown() {
        Scope.getCurrentScope().getSingleton(ExecutorService::class.java)
            .clearExecutor("jdbc", database)
    }

    @Test
    fun `release without acquisition sends no SQL even if another process owns the lock`() {
        service.releaseLock()
        verifyNoInteractions(executor)
    }

    @Test
    fun `acquired lock is released once and repeated release is a no-op`() {
        assertTrue(service.acquireLock())
        assertTrue(service.hasChangeLogLock())
        service.releaseLock()
        service.releaseLock()
        assertFalse(service.hasChangeLogLock())
        verify(executor, times(1)).update(any(UnlockDatabaseChangeLogStatement::class.java))
    }

    @Test
    fun `failed acquisition cannot release another process lock`() {
        `when`(executor.queryForObject(any(SelectFromDatabaseChangeLogLockStatement::class.java),
            eq(Boolean::class.javaObjectType))).thenReturn(true)
        assertFalse(service.acquireLock())
        service.releaseLock()
        verify(executor, never()).update(any(UnlockDatabaseChangeLogStatement::class.java))
    }

    @Test
    fun `fresh service can force release an abandoned lock`() {
        `when`(executor.queryForObject(any(SelectFromDatabaseChangeLogLockStatement::class.java),
            eq(Boolean::class.javaObjectType))).thenReturn(true)
        assertFalse(service.hasChangeLogLock())
        service.forceReleaseLock()
        verify(service).init()
        verify(executor).update(any(UnlockDatabaseChangeLogStatement::class.java))
        assertFalse(service.hasChangeLogLock())
    }

    @Test
    fun `real release errors propagate`() {
        assertTrue(service.acquireLock())
        `when`(executor.update(any(UnlockDatabaseChangeLogStatement::class.java)))
            .thenThrow(DatabaseException("simulated update failure"))
        assertThrows(LockException::class.java) { service.releaseLock() }
    }

    @Test
    fun `forced release errors propagate`() {
        `when`(executor.queryForObject(any(SelectFromDatabaseChangeLogLockStatement::class.java),
            eq(Boolean::class.javaObjectType))).thenReturn(true)
        `when`(executor.update(any(UnlockDatabaseChangeLogStatement::class.java)))
            .thenThrow(DatabaseException("simulated update failure"))
        assertThrows(LockException::class.java) { service.forceReleaseLock() }
    }

    @Test
    fun `unexpected affected row count remains an error when lock was acquired`() {
        assertTrue(service.acquireLock())
        `when`(executor.update(any(UnlockDatabaseChangeLogStatement::class.java))).thenReturn(0)
        assertThrows(LockException::class.java) { service.releaseLock() }
    }
    @Test
    fun `force release of an already unlocked table is idempotent`() {
        service.forceReleaseLock()
        verify(executor, never()).update(any(UnlockDatabaseChangeLogStatement::class.java))
    }

    @Test
    fun `reset invalidates initialization state before reconnect`() {
        `when`(executor.queryForInt(any(liquibase.statement.core.RawSqlStatement::class.java)))
            .thenReturn(1, 0)
        assertTrue(service.isDatabaseChangeLogLockTableInitialized(false))
        service.reset()
        assertFalse(service.isDatabaseChangeLogLockTableInitialized(false))
        verify(executor, times(2)).queryForInt(any(liquibase.statement.core.RawSqlStatement::class.java))
    }

    @Test
    fun `catalog reservation contention cannot acquire the lock row`() {
        `when`(executor.updatesDatabase()).thenReturn(true)
        doThrow(DatabaseException(java.sql.SQLSyntaxErrorException("Exists", "42S01", 1050)))
            .`when`(executor).execute(any(liquibase.statement.core.RawSqlStatement::class.java))
        assertFalse(service.acquireLock())
        verify(executor, never()).update(any(LockDatabaseChangeLogStatement::class.java))
    }

    @Test
    fun `catalog permission failures remain visible instead of being treated as contention`() {
        `when`(executor.updatesDatabase()).thenReturn(true)
        doThrow(DatabaseException(java.sql.SQLSyntaxErrorException("Denied", "42000", 1142)))
            .`when`(executor).execute(any(liquibase.statement.core.RawSqlStatement::class.java))
        assertThrows(LockException::class.java) { service.acquireLock() }
        verify(executor, never()).update(any(LockDatabaseChangeLogStatement::class.java))
    }

    @Test
    fun `legacy duplicate-view error is contention but unrelated syntax errors fail`() {
        `when`(executor.updatesDatabase()).thenReturn(true)
        doThrow(DatabaseException(java.sql.SQLSyntaxErrorException("Unexpected exception: Table 'DATABASECHANGELOGLOCK_MUTEX' already exists", "HY000", 1064)))
            .`when`(executor).execute(any(liquibase.statement.core.RawSqlStatement::class.java))
        assertFalse(service.acquireLock())
        doThrow(DatabaseException(java.sql.SQLSyntaxErrorException("Getting syntax error", "HY000", 1064)))
            .`when`(executor).execute(any(liquibase.statement.core.RawSqlStatement::class.java))
        assertThrows(LockException::class.java) { service.acquireLock() }
    }

}
