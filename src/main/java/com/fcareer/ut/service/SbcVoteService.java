package com.fcareer.ut.service;

import com.fcareer.ut.entity.SbcVoteEntity;
import com.fcareer.ut.model.SbcVoteModels.SetVotes;
import com.fcareer.ut.model.SbcVoteModels.VoteSummary;
import com.fcareer.ut.repository.SbcVoteRepository;
import com.fcareer.ut.repository.SbcVoteRepository.VoteTotals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SbcVoteService {

    private final SbcVoteRepository repository;

    public VoteSummary summary(Long userId) {
        Map<Long, Short> mine = repository.findByUserId(userId).stream().collect(Collectors.toMap(SbcVoteEntity::getSetId, SbcVoteEntity::getVote));
        return new VoteSummary(repository.totals().stream().map(t -> toView(t, mine)).toList());
    }

    @Transactional
    public SetVotes vote(Long userId, Long setId, int vote) {
        repository.findByUserIdAndSetId(userId, setId).ifPresentOrElse(existing -> {
            if (vote == 0) {
                repository.delete(existing);
            } else {
                existing.setVote((short) vote);
            }
        }, () -> {
            if (vote != 0) {
                SbcVoteEntity entity = new SbcVoteEntity();
                entity.setUserId(userId);
                entity.setSetId(setId);
                entity.setVote((short) vote);
                repository.save(entity);
            }
        });
        repository.flush();
        Map<Long, Short> mine = new HashMap<>();
        repository.findByUserIdAndSetId(userId, setId).ifPresent(v -> mine.put(setId, v.getVote()));
        return repository.totals().stream().filter(t -> Objects.equals(t.getSetId(), setId)).findFirst()
                .map(t -> toView(t, mine)).orElse(new SetVotes(setId, 0, 0, 0));
    }

    private static SetVotes toView(VoteTotals totals, Map<Long, Short> mine) {
        return new SetVotes(totals.getSetId(), totals.getUp(), totals.getDown(), mine.getOrDefault(totals.getSetId(), (short) 0));
    }
}
