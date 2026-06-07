package com.example.scheduler.infrastructure.persistence.adapter;

import com.example.scheduler.application.port.in.JobFilter;
import com.example.scheduler.application.port.out.JobRepositoryPort;
import com.example.scheduler.domain.model.Job;
import com.example.scheduler.domain.model.JobState;
import com.example.scheduler.infrastructure.persistence.entity.JobEntity;
import com.example.scheduler.infrastructure.persistence.entity.TaskQueueEntity;
import com.example.scheduler.infrastructure.persistence.mapper.PersistenceMapper;
import com.example.scheduler.infrastructure.persistence.repository.JpaJobRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Repository;

@Repository
public class JobPersistenceAdapter implements JobRepositoryPort {

    private final JpaJobRepository repository;
    private final EntityManager entityManager;

    public JobPersistenceAdapter(JpaJobRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public Job save(Job job) {
        JobEntity entity = repository.findById(job.id()).orElseGet(JobEntity::new);
        TaskQueueEntity queue = entityManager.getReference(TaskQueueEntity.class, job.queueId());
        PersistenceMapper.apply(job, entity, queue);
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return repository.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Job> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Job> find(JobFilter filter) {
        StringBuilder jpql = new StringBuilder("select j from JobEntity j join fetch j.queue q where 1 = 1");
        if (filter.state() != null) {
            jpql.append(" and j.state = :state");
        }
        if (filter.queueName() != null && !filter.queueName().isBlank()) {
            jpql.append(" and q.name = :queueName");
        }
        jpql.append(" order by j.createdAt desc");

        var query = entityManager.createQuery(jpql.toString(), JobEntity.class);
        if (filter.state() != null) {
            query.setParameter("state", filter.state());
        }
        if (filter.queueName() != null && !filter.queueName().isBlank()) {
            query.setParameter("queueName", filter.queueName());
        }
        query.setFirstResult(Math.max(0, filter.offset()));
        query.setMaxResults(Math.max(1, Math.min(filter.limit(), 500)));
        return query.getResultList().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public long count(JobFilter filter) {
        StringBuilder jpql = new StringBuilder("select count(j) from JobEntity j join j.queue q where 1 = 1");
        if (filter.state() != null) {
            jpql.append(" and j.state = :state");
        }
        if (filter.queueName() != null && !filter.queueName().isBlank()) {
            jpql.append(" and q.name = :queueName");
        }
        var query = entityManager.createQuery(jpql.toString(), Long.class);
        if (filter.state() != null) {
            query.setParameter("state", filter.state());
        }
        if (filter.queueName() != null && !filter.queueName().isBlank()) {
            query.setParameter("queueName", filter.queueName());
        }
        return query.getSingleResult();
    }

    @Override
    public List<Job> findDueJobsForUpdate(Instant now, List<String> queueNames, int limit) {
        String sql = """
                select j.*
                from jobs j
                join queues q on q.id = j.queue_id
                where j.state in ('QUEUED', 'SCHEDULED', 'RETRYING')
                  and j.scheduled_at <= :now
                  and q.paused = false
                """;
        if (queueNames != null && !queueNames.isEmpty()) {
            String placeholders = IntStream.range(0, queueNames.size())
                    .mapToObj(index -> ":queueName" + index)
                    .collect(Collectors.joining(", "));
            sql += " and q.name in (" + placeholders + ")";
        }
        sql += " order by j.priority desc, j.created_at asc for update skip locked limit :limit";
        Query query = entityManager.createNativeQuery(sql, JobEntity.class)
                .setParameter("now", now)
                .setParameter("limit", limit);
        if (queueNames != null && !queueNames.isEmpty()) {
            for (int index = 0; index < queueNames.size(); index++) {
                query.setParameter("queueName" + index, queueNames.get(index));
            }
        }
        return entities(query).stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public List<Job> findExpiredLeasesForUpdate(Instant now, int limit) {
        Query query = entityManager.createNativeQuery("""
                select *
                from jobs
                where state in ('LEASED', 'RUNNING')
                  and lease_expires_at < :now
                order by lease_expires_at asc
                for update skip locked
                limit :limit
                """, JobEntity.class)
                .setParameter("now", now)
                .setParameter("limit", limit);
        return entities(query).stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public Map<JobState, Long> countByState() {
        Map<JobState, Long> counts = new EnumMap<>(JobState.class);
        Arrays.stream(JobState.values()).forEach(state -> counts.put(state, 0L));
        for (Object[] row : repository.countByStateGrouped()) {
            counts.put((JobState) row[0], (Long) row[1]);
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private List<JobEntity> entities(Query query) {
        return query.getResultList();
    }
}
