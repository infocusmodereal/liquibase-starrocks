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
        executor = mock(Executor::class.java)
        service = spy(StarRocksLockService())
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
}
