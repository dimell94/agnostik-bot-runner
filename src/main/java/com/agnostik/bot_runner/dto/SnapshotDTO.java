package com.agnostik.bot_runner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotDTO {

    private UserView me;
    private NeighborView left;
    private NeighborView right;
    private CorridorInfo corridor;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserView {

        private Long id;
        private String text;
        private boolean locked;
        private Integer myIndex;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NeighborView {

        private Long id;
        private String text;
        private boolean locked;
        private boolean friend;
        private boolean requestToMe;
        private boolean requestFromMe;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorridorInfo {
        private Integer size;
    }
}



