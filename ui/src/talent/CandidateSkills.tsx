import { Button, Popover, Tag } from 'antd';
import { useRef, useState } from 'react';
import './candidate-skills.css';

const PREVIEW_SKILL_COUNT = 8;

export function CandidateSkills({ skills }: { skills: { id: string; skillName: string }[] }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const close = () => { setOpen(false); trigger.current?.focus(); };
  return <div className="candidate-skills-preview">
    {skills.slice(0, PREVIEW_SKILL_COUNT).map(skill => <span key={skill.id}>{skill.skillName}</span>)}
    {skills.length > PREVIEW_SKILL_COUNT ? <Popover open={open} onOpenChange={setOpen} trigger="click" placement="bottom"
      content={<section className="candidate-skills-panel" aria-label="全部核心技能" onKeyDown={event => {
        if (event.key === 'Escape') { event.stopPropagation(); close(); }
      }}>
        <div className="candidate-skills-heading"><strong>核心技能 · {skills.length} 项</strong><Button type="text" size="small" onClick={close}>收起</Button></div>
        <div className="candidate-skills-list" tabIndex={0}>
          {skills.map(skill => <Tag key={skill.id}>{skill.skillName}</Tag>)}
        </div>
      </section>}>
      <Button ref={trigger} type="link" size="small" aria-label={`查看全部 ${skills.length} 项技能`} aria-expanded={open}
        onKeyDown={event => { if (event.key === 'Escape') close(); }}>+{skills.length - PREVIEW_SKILL_COUNT} 项</Button>
    </Popover> : null}
  </div>;
}
