import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { CandidateGenderIcon } from './CandidateGenderIcon';

it('labels male and female icons and hides missing or unspecified gender', () => {
  const view = render(<CandidateGenderIcon gender="MALE" />);
  const male = screen.getByRole('img', { name: '男' });
  expect(male).toHaveClass('candidate-gender-icon');
  const maleColor = male.style.color;
  view.rerender(<CandidateGenderIcon gender="FEMALE" />);
  expect(screen.getByRole('img', { name: '女' }).style.color).not.toBe(maleColor);
  for (const gender of [undefined, null, 'OTHER', 'UNKNOWN', '']) {
    view.rerender(<CandidateGenderIcon gender={gender} />);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  }
});
