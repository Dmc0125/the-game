# Shapes game

## Scoring

- Each placed cell adds 1 point
  - Unless it's forced to a different location beucase of time, then it subtracts 10 points

- Each cleared row or column adds 10 points per cell
  - If row and column are cleared simultaneously with an intersection, and the multiplier is applied on both of them, it's harder to clear this, than multiple rows or columns separately

- Each cleared row or column add 1 to the score multiplier, that is preserved to the next round
- The multiplier is bumped down by 1 after each round that no cells are cleared, stopping at 1

## Announcer

- update at each line pop
- at the end, animate to 0 white the scoreboard animates up ?

## TODO

### MVP Game

- Better scoring system
- Scoring animation (show +x when on each cell, fly towards score, animate score)
- Announcer
  - for multi fills
- [x] explosion animation
  - start from the last placed cell
  - particles
  - explosion wave ??
  - screen shake
- place time ?
- better debug experience
  - debug menu - toggle on each screen
  - allow to modify values like animations duration, ...
- Place animation (pulse)

### Next steps

- rogue-lite elements
  - ???
  - display current element
  - cover 1 part of the playground
    - reduces playground size, makes the fill amount of cells smaller
  - change the shape of fill condition
  - gravity for cell color for some amount of time
  - make some cells undeleteable for some time

- Improve explosion animation
  - rigt now it slowly chrages up and then explodes into particles
  - instead, quickly build up, explode and then slowly dissappear
